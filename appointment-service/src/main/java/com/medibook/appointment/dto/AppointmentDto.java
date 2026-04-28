package com.medibook.appointment.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

public class AppointmentDto {

    @Data
    public static class BookAppointmentRequest {

        @NotNull(message = "patientId is required")
        private Integer patientId;

        @NotNull(message = "providerId is required")
        private Integer providerId;

        @NotNull(message = "slotId is required")
        private Integer slotId;

        @NotBlank(message = "serviceType is required")
        private String serviceType;

        @NotBlank(message = "modeOfConsultation is required")
        private String modeOfConsultation;

        private String notes;
    }

    @Data
    public static class RescheduleRequest {

        @NotNull(message = "newSlotId is required")
        private Integer newSlotId;

        private String reason;
    }

    @Data
    public static class CancelRequest {
        private String reason;
    }

    @Data
    public static class AppointmentResponse {
        private int appointmentId;
        private int patientId;
        private int providerId;
        private int slotId;
        private String serviceType;
        private String appointmentDate;
        private String startTime;
        private String endTime;
        private String status;
        private String modeOfConsultation;
        private String notes;
        private String cancellationReason;
        private String createdAt;
        private String updatedAt;
    }

    @Data
    public static class AppointmentSummary {
        private int appointmentId;
        private int patientId;
        private int providerId;
        private String serviceType;
        private String appointmentDate;
        private String startTime;
        private String endTime;
        private String status;
        private String modeOfConsultation;
    }

    @Data
    public static class AppointmentCount {
        private int providerId;
        private int total;
        private int completed;
        private int scheduled;
        private int cancelled;
    }
}