package com.cardealer.services;

import com.cardealer.configs.properties.AppProperties;
import com.cardealer.models.*;
import com.cardealer.models.dto.UserResponseDTO;
import com.cardealer.models.request.auth.LoginRequest;
import com.cardealer.models.request.auth.RegisterRequest;
import com.cardealer.models.response.auth.AuthTokensResponse;
import com.cardealer.repositories.EmailVerificationTokenRepository;
import com.cardealer.repositories.PasswordResetTokenRepository;
import com.cardealer.repositories.TokenRepository;
import com.cardealer.repositories.UserRepository;
import com.cardealer.services.constants.AuthConstants;
import com.cardealer.services.exceptions.EmailAlreadyExistsException;
import com.cardealer.services.exceptions.InvalidTokenException;
import com.cardealer.services.security.JwtService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtService jwtService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public void register(RegisterRequest registerRequest) {
        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            log.warn("Attempt to register with existing email: {}", registerRequest.getEmail());
            throw new EmailAlreadyExistsException(registerRequest.getEmail());
        }

        var user = User.builder()
                .name(registerRequest.getName())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);

        String verificationToken = generateAndSendVerificationToken(user);
        log.info("Generated verification token for {}: {}", user.getEmail(), verificationToken);
    }

    public AuthTokensResponse login(LoginRequest loginRequest) {
        var authToken = new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(),
                loginRequest.getPassword()
        );

        authenticationManager.authenticate(authToken);

        var user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user.getEmailVerifiedAt() == null) {
            log.warn("Login attempt with unverified email: {}", user.getEmail());
            throw new RuntimeException("Email not verified. Please check your inbox");
        }

        var accessToken = jwtService.generateAccessToken(user.getEmail(), new HashMap<>());
        var refreshToken = jwtService.generateRefreshToken(user.getEmail(), new HashMap<>());

        revokeAllUserTokens(user);
        saveUserToken(user, accessToken);

        log.info("User logged in successfully: {}", user.getEmail());

        return new AuthTokensResponse(accessToken, refreshToken);
    }

    public void logout(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        tokenRepository.findByToken(token).ifPresentOrElse(
                t -> {
                    t.setRevoked(true);
                    t.setExpired(true);
                    tokenRepository.save(t);
                    log.info("Token revoked on logout: {}", token);
                },
                () -> log.warn("Attempted logout with invalid or missing token")
        );
    }

    public AuthTokensResponse refreshToken(String accessToken, String refreshToken) {
        String email = jwtService.extractEmail(refreshToken);

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!jwtService.isTokenValid(refreshToken, user.getEmail())) {
            log.warn("Invalid refresh token for user: {}", user.getEmail());
            throw new InvalidTokenException("Invalid refresh token");
        }

        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), new HashMap<>());

        revokeAllUserTokens(user);
        saveUserToken(user, newAccessToken);

        log.info("Refresh token successful for user: {}", user.getEmail());

        return new AuthTokensResponse(newAccessToken, refreshToken);
    }

    public UserResponseDTO getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                authentication.getPrincipal().equals("anonymousUser")) {
            log.warn("Unauthorized access attempt detected");
            throw new AccessDeniedException("Access denied. Please authenticate");
        }

        String email = authentication.getName();

        if (email == null || email.isBlank()) {
            log.error("Authentication context returned invalid email");
            throw new InvalidTokenException("Invalid or expired token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        log.info("Authenticated user profile fetched: {}", user.getEmail());

        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        passwordResetTokenRepository.invalidateAllForUser(user);

        String resetToken = UUID.randomUUID().toString();

        PasswordResetToken token = PasswordResetToken.builder()
                .token(resetToken)
                .user(user)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(AuthConstants.PASSWORD_RESET_TOKEN_EXPIRATION_HOURS))
                .used(false)
                .build();

        passwordResetTokenRepository.save(token);

        String resetLink = appProperties.getFrontendUrl() + "/reset-password?token=" + resetToken;
        mailService.sendResetPasswordEmail(user.getEmail(), user.getName(), resetLink);

        log.info("Generated password reset token for {}: {}", user.getEmail(), resetToken);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid or missing reset token"));

        if (resetToken.isUsed()) {
            log.warn("Attempt to reuse a used reset token");
            throw new InvalidTokenException("Reset token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Attempt to use an expired reset token");
            throw new InvalidTokenException("Reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        revokeAllUserTokens(user);

        log.info("Password reset successfully for user: {}", user.getEmail());
    }

    @Transactional
    public AuthTokensResponse verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid or missing token"));

        if (verificationToken.isUsed()) {
            log.warn("Attempt to reuse an already used email verification token");
            throw new InvalidTokenException("Token has already been used");
        }

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Attempt to use an expired email verification token");
            throw new InvalidTokenException("Token has expired");
        }

        User user = verificationToken.getUser();
        user.setEmailVerifiedAt(LocalDateTime.now());
        userRepository.save(user);

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);

        String accessToken = jwtService.generateAccessToken(user.getEmail(), new HashMap<>());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail(), new HashMap<>());

        saveUserToken(user, accessToken);

        log.info("Email verified successfully for user: {}", user.getEmail());

        return new AuthTokensResponse(accessToken, refreshToken);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user.getEmailVerifiedAt() != null) {
            log.warn("Attempted resend of verification email for already verified user: {}", user.getEmail());
            throw new RuntimeException("Email is already verified");
        }

        emailVerificationTokenRepository.invalidateAllForUser(user);

        String verificationToken = generateAndSendVerificationToken(user);
        log.info("Resent verification token for {}: {}", user.getEmail(), verificationToken);
    }

    // --- Métodos privados auxiliares

    private void saveUserToken(User user, String jwtToken) {
        var token = Token.builder()
                .user(user)
                .token(jwtToken)
                .expired(false)
                .revoked(false)
                .build();

        tokenRepository.save(token);
    }

    private void revokeAllUserTokens(User user) {
        List<Token> tokens = tokenRepository.findAllByUser(user);
        if (tokens.isEmpty()) return;

        tokens.forEach(t -> {
            t.setRevoked(true);
            t.setExpired(true);
        });

        tokenRepository.saveAll(tokens);
    }

    private String extractToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header");
            throw new InvalidTokenException("Invalid or missing token");
        }
        return header.substring(7);
    }

    private String generateAndSendVerificationToken(User user) {
        String verificationToken = UUID.randomUUID().toString();

        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(verificationToken)
                .user(user)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(AuthConstants.EMAIL_TOKEN_EXPIRATION_HOURS))
                .used(false)
                .build();

        emailVerificationTokenRepository.save(token);

        String verificationLink = appProperties.getFrontendUrl() + "/auth/verify-email?token=" + verificationToken;
        mailService.sendVerificationEmail(user.getEmail(), user.getName(), verificationLink);

        return verificationToken;
    }
}
