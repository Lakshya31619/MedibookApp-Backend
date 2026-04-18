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
@CrossOrigin(origins = "*")
public class AuthResource {

    @Autowired
    private AuthService authService;
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                        "message", "Registration successful",
                        "userId", user.getUserId(),
                        "email", user.getEmail(),
                        "role", user.getRole()
                    ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);  // strip "Bearer "
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
        try {
            User user = authService.getUserById(userId);

            User requester = authService.getUserByEmail(principal.getName());
            if (requester.getUserId() != userId && !requester.getRole().equals("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }

            return ResponseEntity.ok(mapToResponse(user));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/profile/{userId}")
    public ResponseEntity<?> updateProfile(@PathVariable int userId,
                                           @RequestBody UpdateProfileRequest request,
                                           Principal principal) {
        try {
            User requester = authService.getUserByEmail(principal.getName());
            if (requester.getUserId() != userId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You can only update your own profile"));
            }

            User updated = authService.updateProfile(userId, request);
            return ResponseEntity.ok(Map.of(
                "message", "Profile updated",
                "user", mapToResponse(updated)
            ));
        } catch (RuntimeException e) {
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
                : authService.getUsersByRole("PATIENT");

        return ResponseEntity.ok(users.stream().map(this::mapToResponse).toList());
    }

    private Map<String, Object> mapToResponse(User user) {
        return Map.of(
            "userId",       user.getUserId(),
            "fullName",     user.getFullName(),
            "email",        user.getEmail(),
            "phone",        user.getPhone() != null ? user.getPhone() : "",
            "role",         user.getRole(),
            "isActive",     user.isActive(),
            "provider",     user.getProvider() != null ? user.getProvider() : "local",
            "profilePicUrl",user.getProfilePicUrl() != null ? user.getProfilePicUrl() : "",
            "createdAt",    user.getCreatedAt() != null ? user.getCreatedAt().toString() : ""
        );
    }
}