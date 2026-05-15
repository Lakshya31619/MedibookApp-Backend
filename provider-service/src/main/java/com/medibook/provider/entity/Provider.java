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
        this.available    = false;
        this.verified     = false;
        this.emailVerified = false;
        this.avgRating    = 0.0;
        if (this.verificationStatus == null) {
            this.verificationStatus = "PENDING";
        }
    }
}