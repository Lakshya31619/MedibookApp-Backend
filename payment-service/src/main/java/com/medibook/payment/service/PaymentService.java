package com.medibook.payment.service;

import com.medibook.payment.dto.PaymentDto.*;
import com.medibook.payment.entity.Payment;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentService {

    Payment processPayment(ProcessPaymentRequest request);

    Payment getPaymentById(int paymentId);

    Payment getPaymentByAppointment(int appointmentId);

    List<Payment> getPaymentsByPatient(int patientId);

    List<Payment> getPaymentsByProvider(int providerId);

    List<Payment> getAllPayments();

    List<Payment> getPaymentsByDateRange(LocalDateTime start, LocalDateTime end);

    Payment refundPayment(int appointmentId, String reason);

    String getPaymentStatus(int appointmentId);

    void updatePaymentStatus(int paymentId, String status);

    Payment confirmCashPayment(int appointmentId);

    Invoice generateInvoice(int appointmentId);

    EarningsSummary getEarningsSummary(int providerId);

    double getTotalRevenue();

    PlatformRevenue getPlatformRevenue();

    double getPatientTotalSpending(int patientId);
}