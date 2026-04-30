package com.medibook.payment.resource;

import com.medibook.payment.dto.PaymentDto.*;
import com.medibook.payment.entity.Payment;
import com.medibook.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentResource {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/process")
    public ResponseEntity<?> process(@Valid @RequestBody ProcessPaymentRequest request) {
        try {
            Payment payment = paymentService.processPayment(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toResponse(payment));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<?> getByAppointment(@PathVariable int appointmentId) {
        try {
            return ResponseEntity.ok(
                toResponse(paymentService.getPaymentByAppointment(appointmentId)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<PaymentSummary>> getByPatient(@PathVariable int patientId) {
        return ResponseEntity.ok(
            paymentService.getPaymentsByPatient(patientId)
                .stream().map(this::toSummary).toList()
        );
    }

    @GetMapping("/status/{appointmentId}")
    public ResponseEntity<?> getStatus(@PathVariable int appointmentId) {
        String status = paymentService.getPaymentStatus(appointmentId);
        return ResponseEntity.ok(Map.of(
            "appointmentId", appointmentId,
            "status", status
        ));
    }

    @GetMapping("/invoice/{appointmentId}")
    public ResponseEntity<?> getInvoice(@PathVariable int appointmentId) {
        try {
            return ResponseEntity.ok(paymentService.generateInvoice(appointmentId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/patient/{patientId}/total")
    public ResponseEntity<?> getPatientTotal(@PathVariable int patientId) {
        double total = paymentService.getPatientTotalSpending(patientId);
        return ResponseEntity.ok(Map.of(
            "patientId", patientId,
            "totalSpending", total,
            "currency", "INR"
        ));
    }

    @PostMapping("/refund/{appointmentId}")
    public ResponseEntity<?> refund(
            @PathVariable int appointmentId,
            @RequestBody(required = false) RefundRequest request) {
        try {
            String reason = (request != null && request.getReason() != null)
                ? request.getReason() : "Appointment cancelled";
            Payment payment = paymentService.refundPayment(appointmentId, reason);
            return ResponseEntity.ok(Map.of(
                "message", "Refund processed",
                "status", payment.getStatus(),
                "refundTransactionId",
                    payment.getRefundTransactionId() != null
                        ? payment.getRefundTransactionId() : "N/A",
                "notes", payment.getNotes() != null ? payment.getNotes() : ""
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/provider/{providerId}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<List<PaymentSummary>> getByProvider(@PathVariable int providerId) {
        return ResponseEntity.ok(
            paymentService.getPaymentsByProvider(providerId)
                .stream().map(this::toSummary).toList()
        );
    }

    @GetMapping("/earnings/{providerId}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<EarningsSummary> getEarnings(@PathVariable int providerId) {
        return ResponseEntity.ok(paymentService.getEarningsSummary(providerId));
    }

    @PostMapping("/confirm-cash/{appointmentId}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> confirmCash(@PathVariable int appointmentId) {
        try {
            Payment payment = paymentService.confirmCashPayment(appointmentId);
            return ResponseEntity.ok(Map.of(
                "message", "Cash payment confirmed",
                "appointmentId", appointmentId,
                "status", payment.getStatus(),
                "paidAt", payment.getPaidAt().toString()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PaymentResponse>> getAll() {
        return ResponseEntity.ok(
            paymentService.getAllPayments()
                .stream().map(this::toResponse).toList()
        );
    }

    @PutMapping("/admin/{paymentId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable int paymentId,
                                           @RequestParam String value) {
        try {
            paymentService.updatePaymentStatus(paymentId, value);
            return ResponseEntity.ok(Map.of(
                "message", "Payment status updated to " + value,
                "paymentId", paymentId
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlatformRevenue> getRevenue() {
        return ResponseEntity.ok(paymentService.getPlatformRevenue());
    }

    @GetMapping("/revenue/total")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getTotalRevenue() {
        return ResponseEntity.ok(Map.of(
            "totalRevenue", paymentService.getTotalRevenue(),
            "currency", "INR"
        ));
    }

    @GetMapping("/admin/range")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PaymentResponse>> getByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime end) {
        return ResponseEntity.ok(
            paymentService.getPaymentsByDateRange(start, end)
                .stream().map(this::toResponse).toList()
        );
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

    private PaymentSummary toSummary(Payment p) {
        PaymentSummary s = new PaymentSummary();
        s.setPaymentId(p.getPaymentId());
        s.setAppointmentId(p.getAppointmentId());
        s.setAmount(p.getAmount());
        s.setStatus(p.getStatus());
        s.setMode(p.getMode());
        s.setPaidAt(p.getPaidAt() != null ? p.getPaidAt().toString() : null);
        s.setCurrency(p.getCurrency());
        return s;
    }
}