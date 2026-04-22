package com.medibook.payment.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments",
       indexes = {
           @Index(name = "idx_appointment", columnList = "appointmentId"),
           @Index(name = "idx_patient",     columnList = "patientId"),
           @Index(name = "idx_provider",    columnList = "providerId"),
           @Index(name = "idx_status",      columnList = "status"),
           @Index(name = "idx_transaction", columnList = "transactionId")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int paymentId;

    @Column(nullable = false, unique = true)
    private int appointmentId;

    @Column(nullable = false)
    private int patientId;

    @Column(nullable = false)
    private int providerId;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(nullable = false)
    private String mode;

    @Column
    private String transactionId;

    @Column(nullable = false)
    private String currency = "INR";

    @Column
    private LocalDateTime paidAt;

    @Column
    private LocalDateTime refundedAt;

    @Column
    private String refundTransactionId;

    @Column
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}