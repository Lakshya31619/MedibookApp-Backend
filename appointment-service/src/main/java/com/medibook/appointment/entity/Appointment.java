package com.medibook.appointment.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "appointments",
       indexes = {
           @Index(name = "idx_patient",  columnList = "patientId"),
           @Index(name = "idx_provider", columnList = "providerId"),
           @Index(name = "idx_status",   columnList = "status"),
           @Index(name = "idx_date",     columnList = "appointmentDate")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int appointmentId;

    @Column(nullable = false)
    private int patientId;

    @Column(nullable = false)
    private int providerId;

    @Column(nullable = false)
    private int slotId;

    @Column(nullable = false)
    private String serviceType;

    @Column(nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private String status = "SCHEDULED";

    @Column(nullable = false)
    private String modeOfConsultation = "IN_PERSON";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column
    private String cancellationReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}