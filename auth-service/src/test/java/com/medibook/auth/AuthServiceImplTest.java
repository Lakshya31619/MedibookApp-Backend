package com.medibook.auth;

import com.medibook.auth.config.JwtUtil;
import com.medibook.auth.dto.AuthDto.*;
import com.medibook.auth.entity.EmailVerification;
import com.medibook.auth.entity.User;
import com.medibook.auth.repository.EmailVerificationRepository;
import com.medibook.auth.repository.UserRepository;
import com.medibook.auth.service.EmailService;
import com.medibook.auth.serviceimpl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private EmailService emailService;
    @Mock private EmailVerificationRepository verificationRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "codeExpiryMinutes", 10);

        testUser = new User();
        testUser.setUserId(1);
        testUser.setFullName("John Doe");
        testUser.setEmail("john@example.com");
        testUser.setPasswordHash("hashedpassword");
        testUser.setRole("PATIENT");
        testUser.setActive(true);
        testUser.setEmailVerified(true);
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    void register_ShouldCreateUser_WhenEmailNotExists() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Jane Doe");
        req.setEmail("jane@example.com");
        req.setPassword("password123");
        req.setRole("PATIENT");

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User result = authService.register(req);

        assertNotNull(result);
        assertEquals("Jane Doe", result.getFullName());
        assertEquals("PATIENT", result.getRole());
        assertFalse(result.isEmailVerified());
        assertFalse(result.isActive());
        verify(userRepository).save(any());
    }

    @Test
    void register_ShouldThrow_WhenEmailAlreadyVerified() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("John Doe");
        req.setEmail("john@example.com");
        req.setPassword("password123");
        req.setRole("PATIENT");

        when(userRepository.findByEmail("john@example.com"))
            .thenReturn(Optional.of(testUser)); // testUser is verified

        assertThrows(RuntimeException.class, () -> authService.register(req));
    }

    @Test
    void register_ShouldReplaceUnverifiedUser_WhenEmailExistsButNotVerified() {
        testUser.setEmailVerified(false);
        testUser.setActive(false);

        RegisterRequest req = new RegisterRequest();
        req.setFullName("John Updated");
        req.setEmail("john@example.com");
        req.setPassword("newpassword");
        req.setRole("PATIENT");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(any())).thenReturn("newhash");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User result = authService.register(req);

        verify(verificationRepository).deleteByEmail("john@example.com");
        verify(userRepository).delete(testUser);
        assertNotNull(result);
    }

    @Test
    void register_ShouldThrow_WhenRoleIsInvalid() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Test");
        req.setEmail("test@example.com");
        req.setPassword("pass1234");
        req.setRole("ADMIN"); // not allowed via registration

        // No stub needed — role check throws before any repository call
        assertThrows(RuntimeException.class, () -> authService.register(req));
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_ShouldReturnToken_WhenCredentialsAreValid() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "hashedpassword")).thenReturn(true);
        when(jwtUtil.generateToken("john@example.com", "PATIENT", 1)).thenReturn("jwt-token");
        when(jwtUtil.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login("john@example.com", "password");

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getUser());
    }

    @Test
    void login_ShouldThrow_WhenEmailNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> authService.login("unknown@example.com", "pass"));
    }

    @Test
    void login_ShouldThrow_WhenEmailNotVerified() {
        testUser.setEmailVerified(false);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> authService.login("john@example.com", "password"));
        assertTrue(ex.getMessage().contains("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void login_ShouldThrow_WhenAccountIsInactive() {
        testUser.setActive(false);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

        assertThrows(RuntimeException.class,
            () -> authService.login("john@example.com", "password"));
    }

    @Test
    void login_ShouldThrow_WhenPasswordDoesNotMatch() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpass", "hashedpassword")).thenReturn(false);

        assertThrows(RuntimeException.class,
            () -> authService.login("john@example.com", "wrongpass"));
    }

    // ── verifyEmail ───────────────────────────────────────────────────────────

    @Test
    void verifyEmail_ShouldActivateUser_WhenCodeIsValid() {
        testUser.setEmailVerified(false);
        testUser.setActive(false);

        EmailVerification verification = new EmailVerification(
            "john@example.com", "123456", LocalDateTime.now().plusMinutes(5));

        when(verificationRepository.findByEmail("john@example.com"))
            .thenReturn(Optional.of(verification));
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

        authService.verifyEmail("john@example.com", "123456");

        assertTrue(testUser.isEmailVerified());
        assertTrue(testUser.isActive());
        verify(userRepository).save(testUser);
    }

    @Test
    void verifyEmail_ShouldThrow_WhenCodeIsWrong() {
        EmailVerification verification = new EmailVerification(
            "john@example.com", "123456", LocalDateTime.now().plusMinutes(5));

        when(verificationRepository.findByEmail("john@example.com"))
            .thenReturn(Optional.of(verification));

        assertThrows(RuntimeException.class,
            () -> authService.verifyEmail("john@example.com", "999999"));
    }

    @Test
    void verifyEmail_ShouldThrow_WhenCodeIsExpired() {
        EmailVerification verification = new EmailVerification(
            "john@example.com", "123456", LocalDateTime.now().minusMinutes(1));

        when(verificationRepository.findByEmail("john@example.com"))
            .thenReturn(Optional.of(verification));

        assertThrows(RuntimeException.class,
            () -> authService.verifyEmail("john@example.com", "123456"));
    }

    @Test
    void verifyEmail_ShouldThrow_WhenCodeAlreadyUsed() {
        EmailVerification verification = new EmailVerification(
            "john@example.com", "123456", LocalDateTime.now().plusMinutes(5));
        verification.setUsed(true);

        when(verificationRepository.findByEmail("john@example.com"))
            .thenReturn(Optional.of(verification));

        assertThrows(RuntimeException.class,
            () -> authService.verifyEmail("john@example.com", "123456"));
    }

    // ── sendVerificationCode ──────────────────────────────────────────────────

    @Test
    void sendVerificationCode_ShouldSendEmail_WhenUserExistsAndNotVerified() {
        testUser.setEmailVerified(false);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
        when(verificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString(), anyString());

        authService.sendVerificationCode("john@example.com");

        verify(verificationRepository).deleteByEmail("john@example.com");
        verify(verificationRepository).save(any());
        verify(emailService).sendVerificationCode(eq("john@example.com"), eq("John Doe"), anyString());
    }

    @Test
    void sendVerificationCode_ShouldThrow_WhenAlreadyVerified() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

        assertThrows(RuntimeException.class,
            () -> authService.sendVerificationCode("john@example.com"));
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_ShouldUpdateFields_WhenProvided() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFullName("John Updated");
        req.setPhone("9876543210");

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User updated = authService.updateProfile(1, req);

        assertEquals("John Updated", updated.getFullName());
        assertEquals("9876543210", updated.getPhone());
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_ShouldUpdate_WhenCurrentPasswordMatches() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("oldpass");
        req.setNewPassword("newpass123");

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldpass", "hashedpassword")).thenReturn(true);
        when(passwordEncoder.encode("newpass123")).thenReturn("newhash");

        authService.changePassword(1, req);

        verify(userRepository).save(testUser);
        assertEquals("newhash", testUser.getPasswordHash());
    }

    @Test
    void changePassword_ShouldThrow_WhenCurrentPasswordWrong() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("wrongpass");
        req.setNewPassword("newpass123");

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpass", "hashedpassword")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.changePassword(1, req));
    }

    // ── getUsersByRole ────────────────────────────────────────────────────────

    @Test
    void getUsersByRole_ShouldReturnList() {
        when(userRepository.findAllByRole("PATIENT")).thenReturn(List.of(testUser));

        List<User> result = authService.getUsersByRole("patient"); // test lowercase normalisation

        assertEquals(1, result.size());
        verify(userRepository).findAllByRole("PATIENT");
    }

    // ── deactivateAccount ─────────────────────────────────────────────────────

    @Test
    void deactivateAccount_ShouldSetActiveFalse() {
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        authService.deactivateAccount(1);

        assertFalse(testUser.isActive());
        verify(userRepository).save(testUser);
    }

    // ── getUserById ───────────────────────────────────────────────────────────

    @Test
    void getUserById_ShouldThrow_WhenNotFound() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authService.getUserById(99));
    }

    // ── validateToken / refreshToken ──────────────────────────────────────────

    @Test
    void validateToken_ShouldReturnTrue_WhenValid() {
        when(jwtUtil.validateToken("valid-token")).thenReturn(true);

        assertTrue(authService.validateToken("valid-token"));
    }

    @Test
    void refreshToken_ShouldReturnNewToken_WhenTokenIsValid() {
        when(jwtUtil.validateToken("old-token")).thenReturn(true);
        when(jwtUtil.getEmailFromToken("old-token")).thenReturn("john@example.com");
        when(jwtUtil.getRoleFromToken("old-token")).thenReturn("PATIENT");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateToken("john@example.com", "PATIENT", 1)).thenReturn("new-token");

        String result = authService.refreshToken("old-token");

        assertEquals("new-token", result);
    }

    @Test
    void refreshToken_ShouldThrow_WhenTokenIsInvalid() {
        when(jwtUtil.validateToken("bad-token")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.refreshToken("bad-token"));
    }
}