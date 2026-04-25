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

        @NotBlank(message = "title is required")
        private String title;

        @NotBlank(message = "message is required")
        private String message;

        private List<String> channels;

        private Integer relatedId;
        private String relatedType;

        private String recipientEmail;
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
        private int notificationId;
        private int recipientId;
        private String type;
        private String title;
        private String message;
        private String channel;
        private int relatedId;
        private String relatedType;
        private boolean isRead;
        private String sentAt;
    }

    @Data
    public static class BulkSendResult {
        private int totalSent;
        private int failed;
        private String message;

        public BulkSendResult(int sent, int failed) {
            this.totalSent = sent;
            this.failed    = failed;
            this.message   = sent + " notifications sent, " + failed + " failed";
        }
    }

    @Data
    public static class UnreadCount {
        private int recipientId;
        private int unreadCount;
    }
}