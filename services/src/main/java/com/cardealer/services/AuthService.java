package com.cardealer.services;

import com.cardealer.models.*;
import com.cardealer.models.request.auth.LoginRequest;
import com.cardealer.models.request.auth.RegisterRequest;
import com.cardealer.models.response.auth.AuthTokensResponse;
import com.cardealer.repositories.EmailVerificationTokenRepository;
import com.cardealer.repositories.PasswordResetTokenRepository;
import com.cardealer.repositories.TokenRepository;
import com.cardealer.repositories.UserRepository;
import com.cardealer.services.exceptions.EmailAlreadyExistsException;
import com.cardealer.services.exceptions.InvalidTokenException;
import com.cardealer.services.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import com.cardealer.configs.properties.AppProperties;

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
    public AuthTokensResponse register(RegisterRequest registerRequest) {
        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException(registerRequest.getEmail());
        }

        var user = User.builder()
                .name(registerRequest.getName())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);

        String verificationToken = UUID.randomUUID().toString();

        var emailToken = EmailVerificationToken.builder()
                .token(verificationToken)
                .user(user)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

        emailVerificationTokenRepository.save(emailToken);

        String verificationLink = appProperties.getFrontendUrl() + "/auth/verify-email?token=" + verificationToken;
        mailService.sendVerificationEmail(user.getEmail(), verificationLink);

        var accessToken = jwtService.generateAccessToken(user.getEmail(), new HashMap<>());
        var refreshToken = jwtService.generateRefreshToken(user.getEmail(), new HashMap<>());

        saveUserToken(user, accessToken);

        return new AuthTokensResponse(accessToken, refreshToken);
    }

    public AuthTokensResponse login(LoginRequest loginRequest) {
        var authToken = new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(),
                loginRequest.getPassword()
        );

        authenticationManager.authenticate(authToken);

        var user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow();

        var accessToken = jwtService.generateAccessToken(user.getEmail(), new HashMap<>());
        var refreshToken = jwtService.generateRefreshToken(user.getEmail(), new HashMap<>());

        revokeAllUserTokens(user);
        saveUserToken(user, accessToken);

        return new AuthTokensResponse(accessToken, refreshToken);
    }

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

    public void logout(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        tokenRepository.findByToken(token).ifPresent(t -> {
            t.setRevoked(true);
            t.setExpired(true);
            tokenRepository.save(t);
        });
    }

    public AuthTokensResponse refreshToken(String accessToken, String refreshToken) {
        String email = jwtService.extractEmail(refreshToken);

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!jwtService.isTokenValid(refreshToken, user.getEmail()))
            throw new RuntimeException("Invalid refresh token");

        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), new HashMap<>());

        revokeAllUserTokens(user);
        saveUserToken(user, newAccessToken);

        return new AuthTokensResponse(newAccessToken, refreshToken);
    }

    public User getUserProfile(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        String email = jwtService.extractEmail(token);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String resetToken = UUID.randomUUID().toString();

        PasswordResetToken token = PasswordResetToken.builder()
                .token(resetToken)
                .user(user)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false)
                .build();

        passwordResetTokenRepository.save(token);

        String link = appProperties.getFrontendUrl() + "/reset-password?token=" + resetToken;

        mailService.sendPasswordResetEmail(user.getEmail(), link);
    }


    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired password reset token"));

        if (resetToken.isUsed()) {
            throw new InvalidTokenException("This password reset token has already been used.");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("This password reset token has expired.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }


    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid or missing token"));

        if (verificationToken.isUsed()) {
            throw new InvalidTokenException("Token has already been used");
        }

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Token has expired");
        }

        User user = verificationToken.getUser();
        user.setEmailVerifiedAt(LocalDateTime.now());
        userRepository.save(user);

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);
    }


    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user.getEmailVerifiedAt() != null) {
            throw new RuntimeException("Email is already verified.");
        }

        String verificationToken = UUID.randomUUID().toString();

        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(verificationToken)
                .user(user)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

        emailVerificationTokenRepository.save(token);

        String link = appProperties.getFrontendUrl() + "/auth/verify-email?token=" + verificationToken;

        mailService.sendVerificationEmail(user.getEmail(), link);
    }


    private String extractToken(String header) {
        if (header == null || !header.startsWith("Bearer "))
            throw new RuntimeException("Token inválido");
        return header.substring(7);
    }
}
