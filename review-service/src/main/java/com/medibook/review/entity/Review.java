package com.medibook.review.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "appointmentId",
                             name = "uk_review_appointment")
       },
       indexes = {
           @Index(name = "idx_provider",    columnList = "providerId"),
           @Index(name = "idx_patient",     columnList = "patientId"),
           @Index(name = "idx_flagged",     columnList = "isFlagged"),
           @Index(name = "idx_review_date", columnList = "reviewDate")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int reviewId;

    @Column(nullable = false, unique = true)
    private int appointmentId;

    @Column(nullable = false)
    private int patientId;

    @Column(nullable = false)
    private int providerId;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private LocalDate reviewDate;

    @Column(nullable = false)
    private boolean isVerified = false;

    @Column(nullable = false)
    private boolean isAnonymous = false;

    @Column(nullable = false)
    private boolean isFlagged = false;

    @Column
    private String flagReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt  = LocalDateTime.now();
        this.reviewDate = LocalDate.now();
    }
}