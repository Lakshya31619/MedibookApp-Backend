package com.medibook.auth.serviceimpl;

import com.medibook.auth.config.JwtUtil;
import com.medibook.auth.dto.AuthDto.*;
import com.medibook.auth.entity.EmailVerification;
import com.medibook.auth.entity.User;
import com.medibook.auth.repository.EmailVerificationRepository;
import com.medibook.auth.repository.UserRepository;
import com.medibook.auth.service.AuthService;
import com.medibook.auth.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailVerificationRepository verificationRepository;

    @Value("${app.verification.code.expiry-minutes:10}")
    private int codeExpiryMinutes;

    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public User register(RegisterRequest request) {

        String role = request.getRole().toUpperCase();
        if (!role.equals("PATIENT") && !role.equals("PROVIDER")) {
            throw new RuntimeException("Role must be PATIENT or PROVIDER");
        }

        Optional<User> existing = userRepository.findByEmail(request.getEmail());
        if (existing.isPresent()) {
            User existingUser = existing.get();
            // If already verified — block re-registration
            if (existingUser.isEmailVerified()) {
                throw new RuntimeException("Email is already registered: " + request.getEmail());
            }
            // If NOT verified — delete old unverified user + any pending code and allow fresh signup
            verificationRepository.deleteByEmail(request.getEmail());
            userRepository.delete(existingUser);
            userRepository.flush(); // ensure delete is flushed before re-insert
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(hashedPassword);
        user.setPhone(request.getPhone());
        user.setRole(role);
        user.setActive(false);
        user.setEmailVerified(false);

        return userRepository.save(user);
    }

    @Override
    public AuthResponse login(String email, String password) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found for: " + email));

        if (!user.isEmailVerified()) {
            throw new RuntimeException("EMAIL_NOT_VERIFIED");
        }

        if (!user.isActive()) {
            throw new RuntimeException("Account is suspended. Please contact support.");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getUserId());

        UserResponse userResponse = mapToUserResponse(user);

        AuthResponse authResponse = new AuthResponse();
        authResponse.setToken(token);
        authResponse.setTokenType("Bearer");
        authResponse.setExpiresIn(jwtUtil.getExpirationMs());
        authResponse.setUser(userResponse);

        return authResponse;
    }

    @Override
    public void logout(String token) {
        System.out.println("User logged out. Token invalidated client-side.");
    }

    @Override
    @Cacheable(value = "tokenValidation", key = "#token")
    public boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }

    @Override
    public String refreshToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            throw new RuntimeException("Token is invalid or expired, please login again.");
        }
        String email = jwtUtil.getEmailFromToken(token);
        String role  = jwtUtil.getRoleFromToken(token);
        User user = getUserByEmail(email);
        return jwtUtil.generateToken(email, role, user.getUserId());
    }

    @Override
    @Cacheable(value = "users", key = "'email:' + #email")
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    @Override
    @Cacheable(value = "users", key = "'id:' + #userId")
    public User getUserById(int userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
    }

    @Override
    public List<User> getUsersByRole(String role) {
        return userRepository.findAllByRole(role.toUpperCase());
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public User updateProfile(int userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getProfilePicUrl() != null) {
            user.setProfilePicUrl(request.getProfilePicUrl());
        }

        return userRepository.save(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public void changePassword(int userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public void deactivateAccount(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        user.setActive(false);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void sendVerificationCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found for: " + email));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email is already verified.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(codeExpiryMinutes);

        verificationRepository.deleteByEmail(email);
        verificationRepository.save(new EmailVerification(email, code, expiry));

        emailService.sendVerificationCode(email, user.getFullName(), code);
    }

    @Override
    @Transactional
    public void verifyEmail(String email, String code) {
        EmailVerification verification = verificationRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No verification code found. Please request a new one."));

        if (verification.isUsed()) {
            throw new RuntimeException("This code has already been used. Please request a new one.");
        }

        if (verification.isExpired()) {
            verificationRepository.deleteByEmail(email);
            throw new RuntimeException("Verification code has expired. Please request a new one.");
        }

        if (!verification.getCode().equals(code)) {
            throw new RuntimeException("Invalid verification code.");
        }

        verification.setUsed(true);
        verificationRepository.save(verification);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found."));
        user.setEmailVerified(true);
        user.setActive(true);
        userRepository.save(user);

        verificationRepository.deleteByEmail(email);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    private UserResponse mapToUserResponse(User user) {
        UserResponse r = new UserResponse();
        r.setUserId(user.getUserId());
        r.setFullName(user.getFullName());
        r.setEmail(user.getEmail());
        r.setPhone(user.getPhone());
        r.setRole(user.getRole());
        r.setProvider(user.getProvider());
        r.setActive(user.isActive());
        r.setProfilePicUrl(user.getProfilePicUrl());
        r.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        return r;
    }
}