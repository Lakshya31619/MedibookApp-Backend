package com.medibook.payment.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

public class PaymentDto {

    @Data
    public static class ProcessPaymentRequest {

        @NotNull(message = "appointmentId is required")
        private Integer appointmentId;

        @NotNull(message = "patientId is required")
        private Integer patientId;

        @NotNull(message = "providerId is required")
        private Integer providerId;

        @NotNull(message = "amount is required")
        @Min(value = 1, message = "Amount must be greater than 0")
        private Double amount;

        @NotBlank(message = "mode is required")
        private String mode;

        private String transactionId;

        private String notes;
    }

    @Data
    public static class RazorpayOrderRequest {

        @NotNull(message = "appointmentId is required")
        private Integer appointmentId;

        @NotNull(message = "patientId is required")
        private Integer patientId;

        @NotNull(message = "providerId is required")
        private Integer providerId;

        @NotNull(message = "amount is required")
        @Min(value = 1, message = "Amount must be greater than 0")
        private Double amount;

        private String notes;
    }

    @Data
    public static class RazorpayOrderResponse {
        private String orderId;
        private String currency;
        private long   amountPaise;
        private String keyId;
    }

    @Data
    public static class RazorpayVerifyRequest {

        @NotBlank(message = "razorpayOrderId is required")
        private String razorpayOrderId;

        @NotBlank(message = "razorpayPaymentId is required")
        private String razorpayPaymentId;

        @NotBlank(message = "razorpaySignature is required")
        private String razorpaySignature;

        @NotNull
        private Integer appointmentId;

        @NotNull
        private Integer patientId;

        @NotNull
        private Integer providerId;

        @NotNull
        @Min(1)
        private Double amount;

        @NotBlank
        private String mode;

        private String notes;
    }

    @Data
    public static class RefundRequest {
        private String reason;
    }

    @Data
    public static class PaymentResponse {
        private int paymentId;
        private int appointmentId;
        private int patientId;
        private int providerId;
        private double amount;
        private String status;
        private String mode;
        private String transactionId;
        private String currency;
        private String paidAt;
        private String refundedAt;
        private String refundTransactionId;
        private String notes;
        private String createdAt;
    }

    @Data
    public static class PaymentSummary {
        private int paymentId;
        private int appointmentId;
        private double amount;
        private String status;
        private String mode;
        private String paidAt;
        private String currency;
    }

    @Data
    public static class EarningsSummary {
        private int providerId;
        private double totalEarned;
        private double pendingAmount;
        private double totalRefunded;
        private double netEarnings;
    }

    @Data
    public static class Invoice {
        private String invoiceNumber;
        private int appointmentId;
        private int patientId;
        private int providerId;
        private double amount;
        private String currency;
        private String mode;
        private String transactionId;
        private String paidAt;
        private String generatedAt;
    }

    @Data
    public static class MonthlyRevenue {
        private int year;
        private int month;
        private String monthName;
        private double revenue;

        public MonthlyRevenue(int year, int month, double revenue) {
            this.year = year;
            this.month = month;
            this.revenue = revenue;
            this.monthName = java.time.Month.of(month).getDisplayName(
                java.time.format.TextStyle.FULL,
                java.util.Locale.ENGLISH
            );
        }
    }

    @Data
    public static class PlatformRevenue {
        private double totalRevenue;
        private List<MonthlyRevenue> monthlyBreakdown;
    }
}