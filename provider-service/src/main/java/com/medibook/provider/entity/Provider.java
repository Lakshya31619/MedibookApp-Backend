package com.medibook.provider.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "providers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Provider implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int providerId;

    @Column(nullable = false, unique = true)
    private int userId;

    @Column
    private String providerName;

    @Column(nullable = false)
    private String specialization;

    @Column(nullable = false)
    private String qualification;

    @Column(nullable = false)
    private int experienceYears;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column
    private String clinicName;

    @Column
    private String clinicAddress;

    // FIX: removed columnDefinition — Hibernate was treating the DB-level
    // DEFAULT as the source of truth and OMITTING this column from the INSERT
    // entirely. Since ddl-auto=update never re-adds a DEFAULT to an existing
    // column, MySQL rejected the INSERT with "Field doesn't have a default value".
    // Plain @Column forces Hibernate to always include it in every INSERT.
    @Column(name = "avg_rating")
    private double avgRating;

    @Column(name = "available")
    private boolean available;

    @Column(name = "verified")
    private boolean verified;

    @Column(name = "email_verified")
    private boolean emailVerified;

    @Column(name = "verification_status", length = 20)
    private String verificationStatus;

    @Column
    private String rejectionReason;

    @Column(name = "consultation_fee")
    private double consultationFee;

    @Column(name = "profile_pic_url")
    private String profilePicUrl;

    @Column(nullable = false, updatable = false)
    private LocalDate createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDate.now();
        // Always set every non-null field explicitly so Hibernate
        // includes them in the INSERT with the correct value —
        // never relying on a DB-level DEFAULT that may not exist.
        // Provider starts as NOT available until email + admin verification
        this.available    = false;
        this.verified     = false;
        this.emailVerified = false;
        this.avgRating    = 0.0;
        if (this.verificationStatus == null) {
            this.verificationStatus = "PENDING";
        }
    }
}