package com.medibook.payment.repository;

import com.medibook.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    Optional<Payment> findByAppointmentId(int appointmentId);

    List<Payment> findByPatientId(int patientId);

    List<Payment> findByProviderId(int providerId);

    List<Payment> findByStatus(String status);

    Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findByProviderIdAndStatus(int providerId, String status);

    @Query("SELECT COALESCE(SUM(p.amount), 0.0) FROM Payment p WHERE " +
           "p.providerId = :providerId AND p.status = 'PAID'")
    double sumPaidAmountByProviderId(@Param("providerId") int providerId);

    @Query("SELECT COALESCE(SUM(p.amount), 0.0) FROM Payment p WHERE " +
           "p.providerId = :providerId AND p.status = 'REFUNDED'")
    double sumRefundedAmountByProviderId(@Param("providerId") int providerId);

    @Query("SELECT COALESCE(SUM(p.amount), 0.0) FROM Payment p WHERE " +
           "p.providerId = :providerId AND p.status = 'PENDING'")
    double sumPendingAmountByProviderId(@Param("providerId") int providerId);

    List<Payment> findByPaidAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(p.amount), 0.0) FROM Payment p WHERE " +
           "p.patientId = :patientId AND p.status = 'PAID'")
    double sumPaidAmountByPatientId(@Param("patientId") int patientId);

    @Query("SELECT COALESCE(SUM(p.amount), 0.0) FROM Payment p WHERE p.status = 'PAID'")
    double getTotalPlatformRevenue();

    @Query("SELECT YEAR(p.paidAt), MONTH(p.paidAt), SUM(p.amount) " +
           "FROM Payment p WHERE p.status = 'PAID' " +
           "GROUP BY YEAR(p.paidAt), MONTH(p.paidAt) " +
           "ORDER BY YEAR(p.paidAt) DESC, MONTH(p.paidAt) DESC")
    List<Object[]> getMonthlyRevenue();

    boolean existsByAppointmentId(int appointmentId);
}