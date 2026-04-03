package com.journaling.service;

import com.journaling.dto.AuthResponse;
import com.journaling.dto.LoginRequest;
import com.journaling.dto.RegisterRequest;
import com.journaling.entity.User;
import com.journaling.exception.DuplicateResourceException;
import com.journaling.metrics.JournalingMetrics;
import com.journaling.repository.UserRepository;
import com.journaling.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private JournalingMetrics metrics;

    @InjectMocks private AuthService authService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("$2b$12$hashed")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── Register ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: happy path returns token + user profile")
    void register_success() {
        given(userRepository.existsByEmail("alice@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("$2b$12$hashed");
        given(userRepository.save(any(User.class))).willReturn(mockUser);
        given(jwtUtil.generateToken(any(), eq("alice@example.com"))).willReturn("jwt-token");

        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "password123");
        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().email()).isEqualTo("alice@example.com");
        assertThat(response.user().name()).isEqualTo("Alice");

        then(metrics).should().incrementAuthSuccess();
        then(userRepository).should().save(any(User.class));
    }

    @Test
    @DisplayName("register: duplicate email throws DuplicateResourceException")
    void register_duplicateEmail_throws() {
        given(userRepository.existsByEmail("alice@example.com")).willReturn(true);

        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "password123");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already registered");

        then(userRepository).should(never()).save(any());
    }

    // ── Login ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: valid credentials return token")
    void login_success() {
        given(userRepository.findByEmail("alice@example.com")).willReturn(Optional.of(mockUser));
        given(passwordEncoder.matches("password123", "$2b$12$hashed")).willReturn(true);
        given(jwtUtil.generateToken(1L, "alice@example.com")).willReturn("jwt-token");

        LoginRequest request = new LoginRequest("alice@example.com", "password123");
        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        then(metrics).should().incrementAuthSuccess();
    }

    @Test
    @DisplayName("login: wrong password throws BadCredentialsException")
    void login_wrongPassword_throws() {
        given(userRepository.findByEmail("alice@example.com")).willReturn(Optional.of(mockUser));
        given(passwordEncoder.matches("wrongpass", "$2b$12$hashed")).willReturn(false);

        LoginRequest request = new LoginRequest("alice@example.com", "wrongpass");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        then(metrics).should().incrementAuthFailure();
        then(jwtUtil).should(never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("login: unknown email throws BadCredentialsException")
    void login_unknownEmail_throws() {
        given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

        LoginRequest request = new LoginRequest("unknown@example.com", "password123");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        then(metrics).should().incrementAuthFailure();
    }
}
