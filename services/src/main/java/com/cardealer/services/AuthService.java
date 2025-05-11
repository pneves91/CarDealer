package com.cardealer.services;

import com.cardealer.configs.properties.AppProperties;
import com.cardealer.mappers.UserMapper;
import com.cardealer.models.*;
import com.cardealer.models.dto.UserResponseDTO;
import com.cardealer.models.request.auth.LoginRequest;
import com.cardealer.models.request.auth.RegisterRequest;
import com.cardealer.models.response.auth.AuthTokensResponse;
import com.cardealer.models.response.auth.RegisterResponse;
import com.cardealer.repositories.EmailVerificationTokenRepository;
import com.cardealer.repositories.PasswordResetTokenRepository;
import com.cardealer.repositories.TokenRepository;
import com.cardealer.repositories.UserRepository;
import com.cardealer.services.constants.AuthConstants;
import com.cardealer.services.exceptions.EmailAlreadyExistsException;
import com.cardealer.services.exceptions.InvalidTokenException;
import com.cardealer.services.exceptions.UnauthorizedException;
import com.cardealer.services.exceptions.UserNotFoundException;
import com.cardealer.services.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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
    private final UserMapper userMapper;

    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        // Regista um novo utilizador e envia email de verificação
        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            log.warn("Attempt to register with existing email: {}", registerRequest.getEmail());
            throw new EmailAlreadyExistsException(registerRequest.getEmail());
        }

        var user = User.builder()
                .name(registerRequest.getName())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(Role.USER)
                .enabled(false)
                .build();

        userRepository.save(user);

        String verificationToken = generateAndSendVerificationToken(user);
        log.info("Generated verification token for {}: {}", user.getEmail(), verificationToken);

        return new RegisterResponse("User registered successfully. Please verify your email.");
    }

    public AuthTokensResponse login(LoginRequest loginRequest) {
        // Autentica o utilizador e gera novo par de tokens
        var authToken = new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(),
                loginRequest.getPassword()
        );

        try {
            authenticationManager.authenticate(authToken);
        } catch (BadCredentialsException ex) {
            log.warn("Failed login attempt with bad credentials for email: {}", loginRequest.getEmail());
            throw new UnauthorizedException("Invalid email or password");
        }

        var user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user.getEmailVerifiedAt() == null) {
            log.warn("Login attempt with unverified email: {}", user.getEmail());
            throw new UnauthorizedException("Email not verified. Please check your inbox");
        }

        String sessionId = UUID.randomUUID().toString();
        var accessToken = jwtService.generateAccessToken(user.getEmail(), new HashMap<>());
        var refreshToken = jwtService.generateRefreshToken(user.getEmail(), new HashMap<>());

        if (appProperties.isSingleSession()) {
            revokeAllUserTokens(user);
        }

        saveUserToken(user, accessToken, sessionId);
        saveUserToken(user, refreshToken, sessionId);

        log.info("User logged in successfully: {}", user.getEmail());
        return new AuthTokensResponse(accessToken, refreshToken);
    }

    public void logout(String authorizationHeader) {
        // Faz logout da sessão atual revogando os tokens associados ao access token
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header during logout");
            throw new InvalidTokenException("Invalid or missing token");
        }

        String token = authorizationHeader.substring(7);

        Token tokenEntity = tokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Token not found"));

        tokenEntity.setRevoked(true);
        tokenEntity.setExpired(true);
        tokenRepository.save(tokenEntity);

        if (appProperties.isSingleSession()) {
            revokeAllUserTokens(tokenEntity.getUser());
        } else {
            revokeTokensBySessionId(tokenEntity.getUser(), tokenEntity.getSessionId());
        }
    }

    @Transactional
    public AuthTokensResponse refreshToken(String refreshToken) {
        // Valida e processa um refresh token, revogando o anterior e criando nova sessão
        String email = jwtService.extractEmail(refreshToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        if (!jwtService.isTokenValid(refreshToken, email)) {
            log.warn("Invalid or expired refresh token for user: {}", email);
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        Token storedToken = tokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired refresh token"));

        if (storedToken.isExpired() || storedToken.isRevoked()) {
            log.warn("Refresh token is expired or revoked for user: {}", email);
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        storedToken.setExpired(true);
        storedToken.setRevoked(true);
        tokenRepository.save(storedToken);

        revokeTokensBySessionId(user, storedToken.getSessionId());

        String sessionId = UUID.randomUUID().toString();
        String newAccessToken = jwtService.generateAccessToken(email, new HashMap<>());
        String newRefreshToken = jwtService.generateRefreshToken(email, new HashMap<>());

        saveUserToken(user, newAccessToken, sessionId);
        saveUserToken(user, newRefreshToken, sessionId);

        log.info("Refresh token accepted. New tokens issued for user: {}", email);
        return new AuthTokensResponse(newAccessToken, newRefreshToken);
    }

    public UserResponseDTO getAuthenticatedUser() {
        // Devolve os dados do utilizador autenticado através do SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            log.warn("Unauthorized access attempt detected");
            throw new UnauthorizedException("Access denied. Please authenticate");
        }

        String email = authentication.getName();
        if (email == null || email.isBlank()) {
            log.error("Authentication context returned invalid email");
            throw new InvalidTokenException("Invalid or expired token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        return userMapper.toResponse(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        // Inicia o processo de recuperação de password, gerando token e enviando email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

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
        // Valida o token de recuperação e atualiza a password do utilizador
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

        userRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        revokeAllUserTokens(user);

        log.info("Password reset successfully for user: {}", user.getEmail());
    }

    @Transactional
    public AuthTokensResponse verifyEmail(String token) {
        // Confirma o email como verificado após validação do token
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Missing verification token");
        }

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

        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtService.generateAccessToken(user.getEmail(), new HashMap<>());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail(), new HashMap<>());

        saveUserToken(user, accessToken, sessionId);
        saveUserToken(user, refreshToken, sessionId);

        log.info("Email verified successfully for user: {}", user.getEmail());

        return new AuthTokensResponse(accessToken, refreshToken);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        // Reenvia o email de verificação para o utilizador com nova validade
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        if (user.getEmailVerifiedAt() != null) {
            log.warn("Attempted resend of verification email for already verified user: {}", user.getEmail());
            throw new InvalidTokenException("Email is already verified");
        }

        emailVerificationTokenRepository.invalidateAllForUser(user);

        String verificationToken = generateAndSendVerificationToken(user);
        log.info("Resent verification token for {}: {}", user.getEmail(), verificationToken);
    }

    // --- Private helpers

    private void saveUserToken(User user, String jwtToken, String sessionId) {
        var token = Token.builder()
                .user(user)
                .token(jwtToken)
                .expired(false)
                .revoked(false)
                .sessionId(sessionId)
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

    private void revokeTokensBySessionId(User user, String sessionId) {
        List<Token> sessionTokens = tokenRepository.findAllByUser(user).stream()
                .filter(t -> sessionId.equals(t.getSessionId()))
                .toList();

        sessionTokens.forEach(t -> {
            t.setRevoked(true);
            t.setExpired(true);
        });

        tokenRepository.saveAll(sessionTokens);
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
