package com.medibook.auth.resource;

import com.medibook.auth.dto.AuthDto.*;
import com.medibook.auth.entity.User;
import com.medibook.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthResource {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = authService.register(request);

            try {
                authService.sendVerificationCode(user.getEmail());
            } catch (Exception mailEx) {
                System.err.println("Warning: could not send verification email to "
                        + user.getEmail() + ": " + mailEx.getMessage());
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                        "message", "Registration successful. Please check your email for the verification code.",
                        "userId", user.getUserId(),
                        "email", user.getEmail(),
                        "role", user.getRole()
                    ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/send-verification")
    public ResponseEntity<?> sendVerification(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            if (email == null || email.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            authService.sendVerificationCode(email);
            return ResponseEntity.ok(Map.of("message", "Verification code sent to " + email));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            String code  = body.get("code");
            if (email == null || code == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email and code are required"));
            }
            authService.verifyEmail(email, code);
            return ResponseEntity.ok(Map.of("message", "Email verified successfully! You can now log in."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if ("EMAIL_NOT_VERIFIED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "EMAIL_NOT_VERIFIED", "email", request.getEmail()));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        authService.logout(token);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader("Authorization") String authHeader) {
        try {
            String token    = authHeader.substring(7);
            String newToken = authService.refreshToken(token);
            return ResponseEntity.ok(Map.of("token", newToken, "tokenType", "Bearer"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/profile/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable int userId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }
        try {
            User user = authService.getUserById(userId);

            User requester;
            try {
                requester = authService.getUserByEmail(principal.getName());
            } catch (Exception e) {
                System.err.println("[AuthResource] Failed to load requester for principal '"
                        + principal.getName() + "': " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Could not verify requester identity"));
            }

            if (requester.getUserId() != userId && !requester.getRole().equals("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }

            return ResponseEntity.ok(mapToResponse(user));

        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "User not found";
            if (msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", msg));
            }
            System.err.println("[AuthResource] Unexpected error fetching profile for userId=" + userId
                    + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal error retrieving profile"));
        }
    }

    @PutMapping("/profile/{userId}")
    public ResponseEntity<?> updateProfile(@PathVariable int userId,
                                           @RequestBody UpdateProfileRequest request,
                                           Principal principal) {
        try {
            System.out.println("[AuthResource] PUT /profile/" + userId);
            System.out.println("[AuthResource] Request body - fullName: '" + request.getFullName() + "', phone: '" + request.getPhone() + "', profilePicUrl: '" + request.getProfilePicUrl() + "'");

            User requester = authService.getUserByEmail(principal.getName());
            if (requester.getUserId() != userId && !requester.getRole().equals("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You can only update your own profile"));
            }

            User updated = authService.updateProfile(userId, request);
            System.out.println("[AuthResource] Updated user fullName in response: '" + updated.getFullName() + "'");

            Map<String, Object> response = Map.of(
                "message", "Profile updated",
                "user", mapToResponse(updated)
            );
            System.out.println("[AuthResource] Sending response with user: " + response.get("user"));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            System.err.println("[AuthResource] Error updating profile: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                             Principal principal) {
        try {
            User requester = authService.getUserByEmail(principal.getName());
            authService.changePassword(requester.getUserId(), request);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/deactivate/{userId}")
    public ResponseEntity<?> deactivateAccount(@PathVariable int userId,
                                                Principal principal) {
        try {
            User requester = authService.getUserByEmail(principal.getName());
            if (requester.getUserId() != userId && !requester.getRole().equals("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }

            authService.deactivateAccount(userId);
            return ResponseEntity.ok(Map.of("message", "Account deactivated"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers(@RequestParam(required = false) String role) {
        List<User> users = (role != null)
                ? authService.getUsersByRole(role)
                : authService.getAllUsers();

        return ResponseEntity.ok(users.stream().map(this::mapToResponse).toList());
    }

    @GetMapping("/internal/users/{userId}")
    public ResponseEntity<?> getUserByIdInternal(@PathVariable int userId) {
        try {
            User user = authService.getUserById(userId);
            return ResponseEntity.ok(Map.of(
                "userId",   user.getUserId(),
                "fullName", user.getFullName(),
                "email",    user.getEmail(),
                "role",     user.getRole()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> mapToResponse(User user) {
        return Map.of(
            "userId",        user.getUserId(),
            "fullName",      user.getFullName(),
            "email",         user.getEmail(),
            "phone",         user.getPhone() != null ? user.getPhone() : "",
            "role",          user.getRole(),
            "active",        user.isActive(),
            "provider",      user.getProvider() != null ? user.getProvider() : "local",
            "profilePicUrl", user.getProfilePicUrl() != null ? user.getProfilePicUrl() : "",
            "createdAt",     user.getCreatedAt() != null ? user.getCreatedAt().toString() : ""
        );
    }
}