package com.medibook.provider.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

public class ProviderDto {

    @Data
    public static class RegisterProviderRequest {

        @NotNull(message = "userId is required")
        private Integer userId;

        @NotBlank(message = "Specialization is required")
        private String specialization;

        @NotBlank(message = "Qualification is required")
        private String qualification;

        @Min(value = 0, message = "Experience years cannot be negative")
        private int experienceYears;

        private String bio;
        private String clinicName;
        private String clinicAddress;
        private double consultationFee;
        private String profilePicUrl;
    }

    @Data
    public static class UpdateProviderRequest {
        private String specialization;
        private String qualification;
        private int experienceYears;
        private String bio;
        private String clinicName;
        private String clinicAddress;
        private double consultationFee;
        private String profilePicUrl;
    }

    @Data
    public static class RejectProviderRequest {
        @NotBlank(message = "Rejection reason is required")
        private String reason;
    }

    @Data
    public static class ProviderResponse {
        private int providerId;
        private int userId;
        private String providerName;       
        private String specialization;
        private String qualification;
        private int experienceYears;
        private String bio;
        private String clinicName;
        private String clinicAddress;
        private double avgRating;
        private boolean available;          
        private boolean verified;           
        private String verificationStatus;
        private String rejectionReason;
        private double consultationFee;
        private String profilePicUrl;
        private String createdAt;
    }

    @Data
    public static class ProviderSummary {
        private int providerId;
        private String providerName;  
        private String specialization;
        private String clinicName;
        private String clinicAddress;
        private double avgRating;
        private boolean available;   
        private double consultationFee;
        private String profilePicUrl;
        private int experienceYears;
    }

    @Data
    public static class SpecializationCount {
        private String specialization;
        private long count;

        public SpecializationCount(String specialization, long count) {
            this.specialization = specialization;
            this.count = count;
        }
    }
}