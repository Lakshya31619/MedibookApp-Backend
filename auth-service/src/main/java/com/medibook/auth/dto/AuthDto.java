package com.medibook.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDto {

    @Data
    public static class RegisterRequest {

        @NotBlank(message = "Full name is required")
        private String fullName;

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        private String phone;       

        @NotBlank(message = "Role is required")
        private String role;
    }

    @Data
    public static class LoginRequest {

        @NotBlank(message = "Email is required")
        @Email
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    public static class ChangePasswordRequest {

        @NotBlank
        private String currentPassword;

        @NotBlank
        @Size(min = 8)
        private String newPassword;
    }

    @Data
    public static class UpdateProfileRequest {
        private String fullName;
        private String phone;
        private String profilePicUrl;
    }

    @Data
    public static class AuthResponse {
        private String token;        
        private String tokenType = "Bearer";
        private long expiresIn;      
        private UserResponse user;   
    }

    @Data
    public static class UserResponse {
        private int userId;
        private String fullName;
        private String email;
        private String phone;
        private String role;
        private String provider;
        private boolean active;
        private String profilePicUrl;
        private String createdAt;
    }
}