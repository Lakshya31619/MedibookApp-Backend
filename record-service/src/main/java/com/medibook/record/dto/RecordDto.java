package com.medibook.record.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

public class RecordDto {

    @Data
    public static class CreateRecordRequest {

        @NotNull(message = "appointmentId is required")
        private Integer appointmentId;

        @NotNull(message = "patientId is required")
        private Integer patientId;

        @NotNull(message = "providerId is required")
        private Integer providerId;

        @NotBlank(message = "diagnosis is required")
        private String diagnosis;

        private String prescription;

        private String notes;

        private String followUpDate;
    }

    @Data
    public static class UpdateRecordRequest {
        private String diagnosis;
        private String prescription;
        private String notes;
        private String followUpDate;
        private String attachmentUrl;
    }

    @Data
    public static class RecordResponse {
        private int recordId;
        private int appointmentId;
        private int patientId;
        private int providerId;
        private String diagnosis;
        private String prescription;
        private String notes;
        private String attachmentUrl;
        private String followUpDate;
        private boolean followUpReminderSent;
        private String createdAt;
        private String updatedAt;
        private boolean editable;
    }

    @Data
    public static class RecordSummary {
        private int recordId;
        private int appointmentId;
        private int providerId;
        private String diagnosis;
        private String followUpDate;
        private String createdAt;
    }
}