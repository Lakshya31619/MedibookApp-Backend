package com.medibook.payment.serviceimpl;

import com.medibook.payment.config.RazorpayConfig;
import com.medibook.payment.dto.PaymentDto.*;
import com.medibook.payment.entity.Payment;
import com.medibook.payment.repository.PaymentRepository;
import com.medibook.payment.service.RazorpayService;
import com.razorpay.Order;
import com.razorpay.Refund;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class RazorpayServiceImpl implements RazorpayService {

    @Autowired
    private RazorpayClient razorpayClient;

    @Autowired
    private RazorpayConfig razorpayConfig;

    @Autowired
    private PaymentRepository paymentRepository;

    // ─── Create Order ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public RazorpayOrderResponse createOrder(RazorpayOrderRequest request) {
        try {
            // Razorpay expects amount in paise (1 INR = 100 paise)
            long amountPaise = Math.round(request.getAmount() * 100);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount",   amountPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt",  "appt_" + request.getAppointmentId());

            JSONObject notes = new JSONObject();
            notes.put("appointmentId", request.getAppointmentId());
            notes.put("patientId",     request.getPatientId());
            notes.put("providerId",    request.getProviderId());
            if (request.getNotes() != null) {
                notes.put("notes", request.getNotes());
            }
            orderRequest.put("notes", notes);

            Order order = razorpayClient.orders.create(orderRequest);
            String orderId = order.get("id");

            // Persist a PENDING payment record immediately so the patient sees
            // "payment pending" on their dashboard if they close the checkout popup.
            // If a record already exists (retry flow), only update it when still PENDING.
            Payment pending = paymentRepository
                .findByAppointmentId(request.getAppointmentId())
                .orElseGet(() -> {
                    Payment p = new Payment();
                    p.setAppointmentId(request.getAppointmentId());
                    p.setPatientId(request.getPatientId());
                    p.setProviderId(request.getProviderId());
                    p.setAmount(request.getAmount());
                    p.setMode("UPI"); // overwritten to the actual mode on verify
                    p.setCurrency("INR");
                    p.setStatus("PENDING");
                    return p;
                });

            // Don't downgrade an already-completed payment
            if (!"PAID".equals(pending.getStatus()) && !"REFUNDED".equals(pending.getStatus())) {
                pending.setStatus("PENDING");
                pending.setNotes("rzp_order=" + orderId
                    + (request.getNotes() != null ? " | " + request.getNotes() : ""));
                paymentRepository.save(pending);
            }

            RazorpayOrderResponse response = new RazorpayOrderResponse();
            response.setOrderId(orderId);
            response.setCurrency(order.get("currency"));
            response.setAmountPaise(((Number) order.get("amount")).longValue());
            response.setKeyId(razorpayConfig.getKeyId());
            return response;

        } catch (RazorpayException e) {
            throw new RuntimeException("Failed to create Razorpay order: " + e.getMessage(), e);
        }
    }

    // ─── Verify Signature & Capture ─────────────────────────────────────────

    @Override
    @Transactional
    public Payment verifyAndCapture(RazorpayVerifyRequest request) {

        // 1. Verify HMAC-SHA256 signature — critical security check.
        if (!isSignatureValid(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature())) {
            throw new RuntimeException(
                "Payment signature verification failed — possible fraud attempt.");
        }

        // 2. Find existing PENDING record (created in createOrder) OR guard against
        //    duplicate PAID records for the same appointment.
        Payment payment = paymentRepository
            .findByAppointmentId(request.getAppointmentId())
            .orElseGet(() -> {
                // Fallback: no pending record found (legacy / race condition) — create fresh
                Payment p = new Payment();
                p.setAppointmentId(request.getAppointmentId());
                p.setPatientId(request.getPatientId());
                p.setProviderId(request.getProviderId());
                p.setAmount(request.getAmount());
                p.setCurrency("INR");
                return p;
            });

        // Don't re-process an already paid appointment
        if ("PAID".equals(payment.getStatus())) {
            return payment;
        }

        // 3. Validate mode
        String mode = request.getMode().toUpperCase();
        if (!List.of("CARD", "UPI", "WALLET").contains(mode)) {
            throw new RuntimeException(
                "Invalid payment mode for Razorpay. Must be CARD, UPI or WALLET.");
        }

        // 4. Update record to PAID
        payment.setMode(mode);
        payment.setStatus("PAID");
        payment.setTransactionId(request.getRazorpayPaymentId());
        payment.setPaidAt(LocalDateTime.now());
        payment.setNotes(request.getNotes() != null
            ? request.getNotes() + " | rzp_order=" + request.getRazorpayOrderId()
            : "rzp_order=" + request.getRazorpayOrderId());

        return paymentRepository.save(payment);
    }

    // ─── Real Razorpay Refund ────────────────────────────────────────────────

    @Override
    @Transactional
    public Payment refundViaRazorpay(int appointmentId, String reason) {

        Payment payment = paymentRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new RuntimeException(
                    "No payment found for appointmentId: " + appointmentId));

        // Handle CASH / non-Razorpay payments
        if (payment.getMode().equals("CASH")) {
            payment.setStatus("CANCELLED");
            payment.setNotes("Cash appointment cancelled — no collection made");
            return paymentRepository.save(payment);
        }

        if (!payment.getStatus().equals("PAID")) {
            payment.setNotes("Cancellation processed — payment was " + payment.getStatus());
            return paymentRepository.save(payment);
        }

        // Only refund if the transactionId looks like a Razorpay payment ID
        String txnId = payment.getTransactionId();
        if (txnId == null || !txnId.startsWith("pay_")) {
            payment.setStatus("REFUNDED");
            payment.setRefundedAt(LocalDateTime.now());
            payment.setNotes("Local refund (non-Razorpay txn). Reason: " + reason);
            return paymentRepository.save(payment);
        }

        try {
            long amountPaise = Math.round(payment.getAmount() * 100);

            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", amountPaise);
            refundRequest.put("speed",  "normal");
            JSONObject refundNotes = new JSONObject();
            refundNotes.put("reason", reason);
            refundRequest.put("notes", refundNotes);

            Refund refund = razorpayClient.payments.refund(txnId, refundRequest);
            String refundId = refund.get("id");

            payment.setStatus("REFUNDED");
            payment.setRefundedAt(LocalDateTime.now());
            payment.setRefundTransactionId(refundId);
            payment.setNotes("Razorpay refund issued. Reason: " + reason
                + ". Refund ID: " + refundId);

            return paymentRepository.save(payment);

        } catch (RazorpayException e) {
            throw new RuntimeException(
                "Razorpay refund failed: " + e.getMessage(), e);
        }
    }

    // ─── Signature Verification Helper ──────────────────────────────────────

    private boolean isSignatureValid(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                razorpayConfig.getKeySecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            ));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            return computed.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}