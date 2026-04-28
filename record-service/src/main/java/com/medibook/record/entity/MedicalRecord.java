package com.medibook.record.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medical_records",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "appointmentId",
                             name = "uk_record_appointment")
       },
       indexes = {
           @Index(name = "idx_patient",     columnList = "patientId"),
           @Index(name = "idx_provider",    columnList = "providerId"),
           @Index(name = "idx_followup",    columnList = "followUpDate"),
           @Index(name = "idx_created",     columnList = "createdAt")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int recordId;

    @Column(nullable = false, unique = true)
    private int appointmentId;

    @Column(nullable = false)
    private int patientId;

    @Column(nullable = false)
    private int providerId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String diagnosis;

    @Column(columnDefinition = "TEXT")
    private String prescription;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column
    private String attachmentUrl;

    @Column
    private LocalDate followUpDate;

    @Column(nullable = false)
    private boolean followUpReminderSent = false;

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