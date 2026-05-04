package com.medibook.auth.service;

import com.medibook.auth.dto.AuthDto.*;
import com.medibook.auth.entity.User;

import java.util.List;

public interface AuthService {

    User register(RegisterRequest request);

    AuthResponse login(String email, String password);

    void logout(String token);

    boolean validateToken(String token);

    String refreshToken(String token);

    User getUserByEmail(String email);

    User getUserById(int userId);

    User updateProfile(int userId, UpdateProfileRequest request);

    void changePassword(int userId, ChangePasswordRequest request);

    void deactivateAccount(int userId);

    List<User> getUsersByRole(String role);

    List<User> getAllUsers();
}