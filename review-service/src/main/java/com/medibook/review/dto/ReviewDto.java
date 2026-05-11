package com.medibook.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Map;

public class ReviewDto {

    @Data
    public static class AddReviewRequest {

        @NotNull(message = "appointmentId is required")
        private Integer appointmentId;

        @NotNull(message = "patientId is required")
        private Integer patientId;

        @NotNull(message = "providerId is required")
        private Integer providerId;

        @NotNull(message = "rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating cannot exceed 5")
        private Integer rating;

        private String comment;

        @JsonProperty("isAnonymous")
        private boolean isAnonymous = false;
    }

    @Data
    public static class UpdateReviewRequest {

        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating cannot exceed 5")
        private Integer rating;

        private String comment;

        @JsonProperty("isAnonymous")
        private Boolean isAnonymous;
    }

    @Data
    public static class FlagReviewRequest {
        @NotBlank(message = "flagReason is required")
        private String flagReason;
    }

    @Data
    public static class ReviewResponse {
        private int reviewId;
        private int appointmentId;
        private int patientId;
        private int providerId;
        private int rating;
        private String comment;
        private String reviewDate;

        @JsonProperty("isVerified")
        private boolean isVerified;

        @JsonProperty("isAnonymous")
        private boolean isAnonymous;

        @JsonProperty("isFlagged")
        private boolean isFlagged;

        private String flagReason;
        private String createdAt;
    }

    @Data
    public static class PublicReview {
        private int reviewId;
        private String patientLabel;
        private int rating;
        private String comment;
        private String reviewDate;

        @JsonProperty("isVerified")
        private boolean isVerified;
    }

    @Data
    public static class RatingSummary {
        private int providerId;
        private double avgRating;
        private int totalReviews;
        private Map<Integer, Long> distribution;
    }
}