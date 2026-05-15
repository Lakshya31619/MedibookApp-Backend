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

@RestController
@RequestMapping("/payments/razorpay")
public class RazorpayResource {

    @Autowired
    private RazorpayService razorpayService;

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@Valid @RequestBody RazorpayOrderRequest request) {
        try {
            RazorpayOrderResponse response = razorpayService.createOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyAndCapture(@Valid @RequestBody RazorpayVerifyRequest request) {
        try {
            Payment payment = razorpayService.verifyAndCapture(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(payment));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("signature verification failed")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

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