package com.medibook.payment;

import com.medibook.payment.dto.PaymentDto.*;
import com.medibook.payment.entity.Payment;
import com.medibook.payment.repository.PaymentRepository;
import com.medibook.payment.serviceimpl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Payment testPayment;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "refundWindowHours", 24);

        testPayment = new Payment();
        testPayment.setPaymentId(1);
        testPayment.setAppointmentId(10);
        testPayment.setPatientId(2);
        testPayment.setProviderId(1);
        testPayment.setAmount(500.0);
        testPayment.setStatus("PAID");
        testPayment.setMode("UPI");
        testPayment.setTransactionId("pay_test123");
        testPayment.setCurrency("INR");
        testPayment.setPaidAt(LocalDateTime.now());
    }

    @Test
    void processPayment_ShouldCreatePaidRecord_ForOnlineMode() {
        ProcessPaymentRequest req = new ProcessPaymentRequest();
        req.setAppointmentId(10);
        req.setPatientId(2);
        req.setProviderId(1);
        req.setAmount(500.0);
        req.setMode("UPI");
        req.setTransactionId("pay_test123");

        when(paymentRepository.existsByAppointmentId(10)).thenReturn(false);
        when(paymentRepository.save(any())).thenReturn(testPayment);

        Payment result = paymentService.processPayment(req);

        assertNotNull(result);
        assertEquals("PAID", result.getStatus());
        verify(paymentRepository).save(any());
    }

    @Test
    void processPayment_ShouldThrow_WhenOnlineModeHasNoTransactionId() {
        ProcessPaymentRequest req = new ProcessPaymentRequest();
        req.setAppointmentId(11);
        req.setPatientId(2);
        req.setProviderId(1);
        req.setAmount(500.0);
        req.setMode("CARD");
        req.setTransactionId(null); // missing

        when(paymentRepository.existsByAppointmentId(11)).thenReturn(false);

        assertThrows(RuntimeException.class,
            () -> paymentService.processPayment(req));
    }

    @Test
    void processPayment_ShouldCreatePendingRecord_ForCash() {
        ProcessPaymentRequest req = new ProcessPaymentRequest();
        req.setAppointmentId(12);
        req.setPatientId(2);
        req.setProviderId(1);
        req.setAmount(500.0);
        req.setMode("CASH");

        Payment cashPayment = new Payment();
        cashPayment.setStatus("PENDING");
        cashPayment.setMode("CASH");

        when(paymentRepository.existsByAppointmentId(12)).thenReturn(false);
        when(paymentRepository.save(any())).thenReturn(cashPayment);

        Payment result = paymentService.processPayment(req);

        assertEquals("PENDING", result.getStatus());
    }

    @Test
    void refundPayment_ShouldRefund_WhenWithinWindow() {
        testPayment.setPaidAt(LocalDateTime.now().minusHours(1)); // 1 hour ago = within 24h
        when(paymentRepository.findByAppointmentId(10))
            .thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any()))
            .thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.refundPayment(10, "Patient cancelled");

        assertEquals("REFUNDED", result.getStatus());
        assertNotNull(result.getRefundedAt());
        assertNotNull(result.getRefundTransactionId());
    }

    @Test
    void refundPayment_ShouldNotRefund_WhenOutsideWindow() {
        testPayment.setPaidAt(LocalDateTime.now().minusHours(30)); // 30h ago = outside 24h
        when(paymentRepository.findByAppointmentId(10))
            .thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any()))
            .thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.refundPayment(10, "Late cancel");

        // Status stays PAID — outside window
        assertEquals("PAID", result.getStatus());
        assertTrue(result.getNotes().contains("outside"));
    }

    @Test
    void refundPayment_ShouldSkipRefund_ForCash() {
        testPayment.setMode("CASH");
        testPayment.setStatus("PENDING");
        when(paymentRepository.findByAppointmentId(10))
            .thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any()))
            .thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.refundPayment(10, "Cancelled");

        assertEquals("CANCELLED", result.getStatus());
    }

    @Test
    void confirmCashPayment_ShouldSetPaid() {
        testPayment.setMode("CASH");
        testPayment.setStatus("PENDING");
        testPayment.setPaidAt(null);

        when(paymentRepository.findByAppointmentId(10))
            .thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any()))
            .thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.confirmCashPayment(10);

        assertEquals("PAID", result.getStatus());
        assertNotNull(result.getPaidAt());
    }

    @Test
    void generateInvoice_ShouldThrow_WhenNotPaid() {
        testPayment.setStatus("PENDING");
        when(paymentRepository.findByAppointmentId(10))
            .thenReturn(Optional.of(testPayment));

        assertThrows(RuntimeException.class,
            () -> paymentService.generateInvoice(10));
    }

    @Test
    void generateInvoice_ShouldReturnInvoice_WhenPaid() {
        when(paymentRepository.findByAppointmentId(10))
            .thenReturn(Optional.of(testPayment));

        Invoice invoice = paymentService.generateInvoice(10);

        assertNotNull(invoice);
        assertEquals(10, invoice.getAppointmentId());
        assertTrue(invoice.getInvoiceNumber().startsWith("INV-10"));
    }

    @Test
    void getPaymentStatus_ShouldReturnNotFound_WhenNoPayment() {
        when(paymentRepository.findByAppointmentId(99))
            .thenReturn(Optional.empty());

        String status = paymentService.getPaymentStatus(99);

        assertEquals("NOT_FOUND", status);
    }
}
