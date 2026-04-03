package com.journaling.service;

import com.journaling.dto.AuthResponse;
import com.journaling.dto.LoginRequest;
import com.journaling.dto.RegisterRequest;
import com.journaling.dto.UserProfile;
import com.journaling.entity.User;
import com.journaling.exception.DuplicateResourceException;
import com.journaling.metrics.JournalingMetrics;
import com.journaling.repository.UserRepository;
import com.journaling.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JournalingMetrics metrics;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        metrics.incrementAuthSuccess();

        return new AuthResponse(token, UserProfile.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    metrics.incrementAuthFailure();
                    return new BadCredentialsException("Invalid credentials");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            metrics.incrementAuthFailure();
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        metrics.incrementAuthSuccess();

        return new AuthResponse(token, UserProfile.from(user));
    }

    @Transactional(readOnly = true)
    public UserProfile getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        return UserProfile.from(user);
    }
}
