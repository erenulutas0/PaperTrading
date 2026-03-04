package com.finance.core.controller;

import com.finance.core.domain.AppUser;
import com.finance.core.repository.UserRepository;
import com.finance.core.security.AuthSessionService;
import com.finance.core.security.AuthSessionTokens;
import com.finance.core.security.InvalidRefreshTokenException;
import com.finance.core.security.JwtTokenService;
import com.finance.core.web.CurrentUserId;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthSessionService authSessionService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Email already in use");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body("Username already taken");
        }

        AppUser user = AppUser.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        AppUser saved = userRepository.save(user);
        return ResponseEntity.ok(toAuthResponse(saved));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<AppUser> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            if (passwordMatches(request.getPassword(), user.getPassword())) {
                // Legacy upgrade path: plaintext -> bcrypt.
                if (!isBcryptHash(user.getPassword())) {
                    user.setPassword(passwordEncoder.encode(request.getPassword()));
                    user = userRepository.save(user);
                }
                return ResponseEntity.ok(toAuthResponse(user));
            }
        }
        return ResponseEntity.status(401).body("Invalid credentials");
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            authSessionService.recordInvalidRefreshAttempt();
            throw new InvalidRefreshTokenException("Missing refresh token");
        }

        AuthSessionTokens tokens = authSessionService.refreshSession(request.getRefreshToken());
        AppUser user = userRepository.findById(tokensUserId(tokens.accessToken()))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token user not found"));
        return ResponseEntity.ok(toAuthResponse(user, tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CurrentUserId(required = false) UUID userId,
            @RequestBody(required = false) LogoutRequest request) {
        boolean revoked = false;
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            authSessionService.revokeRefreshToken(request.getRefreshToken());
            revoked = true;
        }
        if (Boolean.TRUE.equals(request != null ? request.getAllSessions() : null) && userId != null) {
            authSessionService.revokeAllSessions(userId);
            revoked = true;
        }
        if (!revoked && userId != null) {
            authSessionService.revokeAllSessions(userId);
            revoked = true;
        }
        if (!revoked) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    private boolean passwordMatches(String raw, String stored) {
        if (stored == null || raw == null) {
            return false;
        }
        if (isBcryptHash(stored)) {
            return passwordEncoder.matches(raw, stored);
        }
        return stored.equals(raw);
    }

    private boolean isBcryptHash(String value) {
        return value != null && value.startsWith("$2");
    }

    private AuthResponse toAuthResponse(AppUser user) {
        AuthSessionTokens tokens = authSessionService.issueSession(user);
        return toAuthResponse(user, tokens);
    }

    private AuthResponse toAuthResponse(AppUser user, AuthSessionTokens tokens) {
        return new AuthResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                tokens.accessToken(),
                tokens.refreshToken(),
                "Bearer",
                tokens.accessExpiresInSeconds(),
                tokens.refreshExpiresInSeconds());
    }

    public record AuthResponse(
            UUID id,
            String username,
            String email,
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            long refreshExpiresInSeconds) {
    }

    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }

    @Data
    public static class LogoutRequest {
        private String refreshToken;
        private Boolean allSessions;
    }

    private UUID tokensUserId(String accessToken) {
        return jwtTokenService.parseAndValidate(accessToken).userId();
    }

    @Data
    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }
}
