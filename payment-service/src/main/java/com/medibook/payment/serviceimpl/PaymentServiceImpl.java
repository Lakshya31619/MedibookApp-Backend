package com.medibook.payment.serviceimpl;

import com.medibook.payment.dto.PaymentDto.*;
import com.medibook.payment.entity.Payment;
import com.medibook.payment.repository.PaymentRepository;
import com.medibook.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Value("${app.refund.window.hours:24}")
    private int refundWindowHours;

    @Override
    @Transactional
    public Payment processPayment(ProcessPaymentRequest request) {

        if (paymentRepository.existsByAppointmentId(request.getAppointmentId())) {
            return paymentRepository.findByAppointmentId(request.getAppointmentId())
                    .orElseThrow(() -> new RuntimeException("Payment record inconsistency"));
        }

        String mode = request.getMode().toUpperCase();
        if (!List.of("CARD", "UPI", "WALLET", "CASH").contains(mode)) {
            throw new RuntimeException(
                "Invalid payment mode. Must be CARD, UPI, WALLET or CASH");
        }

        Payment payment = new Payment();
        payment.setAppointmentId(request.getAppointmentId());
        payment.setPatientId(request.getPatientId());
        payment.setProviderId(request.getProviderId());
        payment.setAmount(request.getAmount());
        payment.setMode(mode);
        payment.setCurrency("INR");
        payment.setNotes(request.getNotes());

        if (mode.equals("CASH")) {
            payment.setStatus("PENDING");
            payment.setTransactionId("CASH-" + request.getAppointmentId());
        } else {
            if (request.getTransactionId() == null || request.getTransactionId().isBlank()) {
                throw new RuntimeException(
                    "transactionId is required for online payments");
            }
            payment.setStatus("PAID");
            payment.setTransactionId(request.getTransactionId());
            payment.setPaidAt(LocalDateTime.now());
        }

        return paymentRepository.save(payment);
    }

    @Override
    public Payment getPaymentById(int paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException(
                    "Payment not found with id: " + paymentId));
    }

    @Override
    public Payment getPaymentByAppointment(int appointmentId) {
        return paymentRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new RuntimeException(
                    "No payment found for appointmentId: " + appointmentId));
    }

    @Override
    public List<Payment> getPaymentsByPatient(int patientId) {
        return paymentRepository.findByPatientId(patientId);
    }

    @Override
    public List<Payment> getPaymentsByProvider(int providerId) {
        return paymentRepository.findByProviderId(providerId);
    }

    @Override
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    @Override
    public List<Payment> getPaymentsByDateRange(LocalDateTime start, LocalDateTime end) {
        return paymentRepository.findByPaidAtBetween(start, end);
    }

    @Override
    public String getPaymentStatus(int appointmentId) {
        return paymentRepository.findByAppointmentId(appointmentId)
                .map(Payment::getStatus)
                .orElse("NOT_FOUND");
    }

    @Override
    @Transactional
    public Payment refundPayment(int appointmentId, String reason) {

        Payment payment = paymentRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new RuntimeException(
                    "No payment found for appointmentId: " + appointmentId));

        if (payment.getMode().equals("CASH")) {
            payment.setStatus("CANCELLED");
            payment.setNotes("Cash appointment cancelled — no collection made");
            return paymentRepository.save(payment);
        }

        if (!payment.getStatus().equals("PAID")) {
            payment.setNotes("Cancellation processed — payment was " + payment.getStatus());
            return paymentRepository.save(payment);
        }

        LocalDateTime refundDeadline = payment.getPaidAt()
                .plusHours(refundWindowHours);

        if (LocalDateTime.now().isAfter(refundDeadline)) {
            payment.setNotes("Cancellation outside " + refundWindowHours
                + "h refund window — no refund issued. Reason: " + reason);
            return paymentRepository.save(payment);
        }

        String refundTxnId = "REFUND-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase();

        payment.setStatus("REFUNDED");
        payment.setRefundedAt(LocalDateTime.now());
        payment.setRefundTransactionId(refundTxnId);
        payment.setNotes("Refund processed. Reason: " + reason
            + ". Refund TxnId: " + refundTxnId);

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public Payment confirmCashPayment(int appointmentId) {
        Payment payment = getPaymentByAppointment(appointmentId);

        if (!payment.getMode().equals("CASH")) {
            throw new RuntimeException(
                "This endpoint is only for CASH payments");
        }
        if (!payment.getStatus().equals("PENDING")) {
            throw new RuntimeException(
                "Payment is already " + payment.getStatus());
        }

        payment.setStatus("PAID");
        payment.setPaidAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public void updatePaymentStatus(int paymentId, String status) {
        Payment payment = getPaymentById(paymentId);
        payment.setStatus(status.toUpperCase());
        if (status.equalsIgnoreCase("PAID") && payment.getPaidAt() == null) {
            payment.setPaidAt(LocalDateTime.now());
        }
        paymentRepository.save(payment);
    }

    @Override
    public Invoice generateInvoice(int appointmentId) {
        Payment payment = getPaymentByAppointment(appointmentId);

        if (!payment.getStatus().equals("PAID")) {
            throw new RuntimeException(
                "Invoice can only be generated for PAID appointments");
        }

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-" + appointmentId + "-"
            + payment.getPaidAt().getYear());
        invoice.setAppointmentId(appointmentId);
        invoice.setPatientId(payment.getPatientId());
        invoice.setProviderId(payment.getProviderId());
        invoice.setAmount(payment.getAmount());
        invoice.setCurrency(payment.getCurrency());
        invoice.setMode(payment.getMode());
        invoice.setTransactionId(payment.getTransactionId());
        invoice.setPaidAt(payment.getPaidAt().toString());
        invoice.setGeneratedAt(LocalDateTime.now().toString());

        return invoice;
    }

    @Override
    public EarningsSummary getEarningsSummary(int providerId) {
        EarningsSummary summary = new EarningsSummary();
        summary.setProviderId(providerId);

        double earned   = paymentRepository.sumPaidAmountByProviderId(providerId);
        double pending  = paymentRepository.sumPendingAmountByProviderId(providerId);
        double refunded = paymentRepository.sumRefundedAmountByProviderId(providerId);

        summary.setTotalEarned(earned);
        summary.setPendingAmount(pending);
        summary.setTotalRefunded(refunded);
        summary.setNetEarnings(earned - refunded);

        return summary;
    }

    @Override
    public double getTotalRevenue() {
        return paymentRepository.getTotalPlatformRevenue();
    }

    @Override
    public double getPatientTotalSpending(int patientId) {
        return paymentRepository.sumPaidAmountByPatientId(patientId);
    }

    @Override
    public PlatformRevenue getPlatformRevenue() {
        double total = paymentRepository.getTotalPlatformRevenue();

        List<MonthlyRevenue> monthly = paymentRepository.getMonthlyRevenue()
                .stream()
                .map(row -> new MonthlyRevenue(
                    ((Number) row[0]).intValue(),  
                    ((Number) row[1]).intValue(),   
                    ((Number) row[2]).doubleValue() 
                ))
                .collect(Collectors.toList());

        PlatformRevenue report = new PlatformRevenue();
        report.setTotalRevenue(total);
        report.setMonthlyBreakdown(monthly);
        return report;
    }
}