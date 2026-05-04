package com.medibook.auth.serviceimpl;

import com.medibook.auth.config.JwtUtil;
import com.medibook.auth.dto.AuthDto.*;
import com.medibook.auth.entity.User;
import com.medibook.auth.repository.UserRepository;
import com.medibook.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public User register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already registered: " + request.getEmail());
        }

        String role = request.getRole().toUpperCase();
        if (!role.equals("PATIENT") && !role.equals("PROVIDER")) {
            throw new RuntimeException("Role must be PATIENT or PROVIDER");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(hashedPassword);
        user.setPhone(request.getPhone());
        user.setRole(role);
        user.setActive(true);

        return userRepository.save(user);
    }

    @Override
    public AuthResponse login(String email, String password) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found for: " + email));

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
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    @Override
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
    public User updateProfile(int userId, UpdateProfileRequest request) {
        User user = getUserById(userId);

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
    public void changePassword(int userId, ChangePasswordRequest request) {
        User user = getUserById(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void deactivateAccount(int userId) {
        User user = getUserById(userId);
        user.setActive(false);
        userRepository.save(user);
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