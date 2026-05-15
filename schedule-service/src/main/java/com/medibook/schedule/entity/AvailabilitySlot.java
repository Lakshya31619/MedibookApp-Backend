package com.medibook.schedule.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "availability_slots",
       indexes = {
           @Index(name = "idx_provider_date", columnList = "providerId, date"),
           @Index(name = "idx_provider_booked", columnList = "providerId, booked")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilitySlot implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int slotId;

    @Column(nullable = false)
    private int providerId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private boolean booked = false;

    @Column(nullable = false)
    private boolean blocked = false;

    @Column
    private String recurrence = "NONE";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.durationMinutes == 0 && startTime != null && endTime != null) {
            this.durationMinutes = (int) java.time.Duration
                .between(startTime, endTime).toMinutes();
        }
    }
}