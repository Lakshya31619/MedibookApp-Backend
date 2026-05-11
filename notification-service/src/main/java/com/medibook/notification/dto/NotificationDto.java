package com.medibook.notification.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

public class NotificationDto {


    @Data
    public static class SendNotificationRequest {

        @NotNull(message = "recipientId is required")
        private Integer recipientId;

        @NotBlank(message = "type is required")
        private String type;

        private String title;

        private String message;

        private List<String> channels;

        private Integer relatedId;

        private String relatedType;

        private String recipientEmail;

        private String appointmentDate;
        private String appointmentTime;
        private String providerName;
        private String patientName;
        private Double amount;
        private String cancellationReason;
    }


    @Data
    public static class BulkNotificationRequest {

        @NotNull
        private List<Integer> recipientIds;

        @NotBlank
        private String type;

        @NotBlank
        private String title;

        @NotBlank
        private String message;

        private List<String> channels;

        private List<String> recipientEmails;
    }

    @Data
    public static class NotificationResponse {
        private int    notificationId;
        private int    recipientId;
        private String type;
        private String title;
        private String message;
        private String channel;
        private int    relatedId;
        private String relatedType;
        @com.fasterxml.jackson.annotation.JsonProperty("isRead")
        private boolean isRead;
        private String sentAt;
    }

    @Data
    public static class BulkSendResult {
        private int    totalSent;
        private int    failed;
        private String message;

        public BulkSendResult(int sent, int failed) {
            this.totalSent = sent;
            this.failed    = failed;
            this.message   = sent + " notification(s) sent, " + failed + " failed";
        }
    }

    @Data
    public static class UnreadCount {
        private int recipientId;
        private int unreadCount;
    }

    @Data
    public static class AppointmentEventRequest {

        @NotNull
        private String eventType;       

        @NotNull
        private Integer appointmentId;

        @NotNull
        private Integer patientId;

        @NotNull
        private Integer providerId;

        private String appointmentDate;
        private String appointmentTime;
        private String providerName;
        private String patientName;
        private String cancellationReason;
    }

    @Data
    public static class PaymentEventRequest {

        @NotNull
        private String eventType;     

        @NotNull
        private Integer paymentId;

        @NotNull
        private Integer patientId;

        @NotNull
        private Integer adminId;     

        private Double  amount;
        private String  appointmentDate;
        private String  providerName;
        private String  patientEmail;
    }

    @Data
    public static class ProviderEventRequest {

        @NotNull
        private String eventType;

        @NotNull
        private Integer providerId;

        private String providerName;
        private String rejectionReason;
        private String providerEmail;
        private Integer adminId;
    }
}