package com.medibook.payment.resource;

import com.medibook.payment.dto.PaymentDto.*;
import com.medibook.payment.entity.Payment;
import com.medibook.payment.service.RazorpayService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Razorpay-specific endpoints.
 *
 * POST /api/payments/razorpay/create-order  — create a Razorpay order before checkout
 * POST /api/payments/razorpay/verify        — verify signature & persist payment as PAID
 * POST /api/payments/razorpay/refund/{id}   — issue a real Razorpay refund
 */
@RestController
@RequestMapping("/payments/razorpay")
public class RazorpayResource {

    @Autowired
    private RazorpayService razorpayService;

    /**
     * Step 1 of online checkout.
     * Creates a Razorpay order and returns the orderId + publishable key
     * the frontend needs to open the checkout popup.
     */
    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@Valid @RequestBody RazorpayOrderRequest request) {
        try {
            RazorpayOrderResponse response = razorpayService.createOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Step 2 of online checkout — called after the user completes payment in the popup.
     * Verifies the Razorpay signature (HMAC-SHA256) and saves the payment as PAID.
     * Returns 400 if the signature is invalid.
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyAndCapture(@Valid @RequestBody RazorpayVerifyRequest request) {
        try {
            Payment payment = razorpayService.verifyAndCapture(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(payment));
        } catch (RuntimeException e) {
            // Distinguish fraud/signature failure from other errors
            String msg = e.getMessage();
            if (msg != null && msg.contains("signature verification failed")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    /**
     * Issues a real refund via Razorpay for the payment linked to the given appointmentId.
     */
    @PostMapping("/refund/{appointmentId}")
    public ResponseEntity<?> refund(
            @PathVariable int appointmentId,
            @RequestBody(required = false) RefundRequest request) {
        try {
            String reason = (request != null && request.getReason() != null)
                    ? request.getReason() : "Appointment cancelled";
            Payment payment = razorpayService.refundViaRazorpay(appointmentId, reason);
            return ResponseEntity.ok(Map.of(
                "message",             "Refund processed via Razorpay",
                "status",              payment.getStatus(),
                "refundTransactionId", payment.getRefundTransactionId() != null
                                           ? payment.getRefundTransactionId() : "N/A",
                "notes",               payment.getNotes() != null ? payment.getNotes() : ""
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Mapper ─────────────────────────────────────────────────────────────

    private PaymentResponse toResponse(Payment p) {
        PaymentResponse r = new PaymentResponse();
        r.setPaymentId(p.getPaymentId());
        r.setAppointmentId(p.getAppointmentId());
        r.setPatientId(p.getPatientId());
        r.setProviderId(p.getProviderId());
        r.setAmount(p.getAmount());
        r.setStatus(p.getStatus());
        r.setMode(p.getMode());
        r.setTransactionId(p.getTransactionId());
        r.setCurrency(p.getCurrency());
        r.setPaidAt(p.getPaidAt() != null ? p.getPaidAt().toString() : null);
        r.setRefundedAt(p.getRefundedAt() != null ? p.getRefundedAt().toString() : null);
        r.setRefundTransactionId(p.getRefundTransactionId());
        r.setNotes(p.getNotes());
        r.setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
        return r;
    }
}