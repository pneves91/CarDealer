package com.cardealer.services;

import com.cardealer.configs.properties.AppProperties;
import com.cardealer.models.EmailVerificationToken;
import com.cardealer.models.PasswordResetToken;
import com.cardealer.models.Token;
import com.cardealer.models.User;
import com.cardealer.models.request.auth.LoginRequest;
import com.cardealer.models.request.auth.RegisterRequest;
import com.cardealer.models.response.auth.AuthTokensResponse;
import com.cardealer.models.response.auth.RegisterResponse;
import com.cardealer.repositories.EmailVerificationTokenRepository;
import com.cardealer.repositories.PasswordResetTokenRepository;
import com.cardealer.repositories.TokenRepository;
import com.cardealer.repositories.UserRepository;
import com.cardealer.services.exceptions.InvalidTokenException;
import com.cardealer.services.exceptions.UnauthorizedException;
import com.cardealer.services.exceptions.UserNotFoundException;
import com.cardealer.services.security.JwtService;
import com.cardealer.services.testutils.TestUserFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenRepository tokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private MailService mailService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AppProperties appProperties;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    // === REGISTER ===

    @Test
    void shouldRegisterUserSuccessfully() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setName("Test User");
        request.setEmail("test@example.com");
        request.setPassword("password123");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(appProperties.getFrontendUrl()).thenReturn("http://localhost:3000");

        // Act
        RegisterResponse response = authService.register(request);

        // Assert
        assertNotNull(response);
        assertEquals("User registered successfully. Please verify your email.", response.getMessage());

        verify(userRepository, times(1)).save(any(User.class));
        verify(emailVerificationTokenRepository, times(1)).save(any(EmailVerificationToken.class));
        verify(mailService, times(1)).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setName("Test User");
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User existingUser = new User();
        existingUser.setEmail("test@example.com");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(existingUser));

        // Act & Assert
        var exception = assertThrows(RuntimeException.class, () -> authService.register(request));
        assertEquals("Email is already in use: test@example.com", exception.getMessage());

        verify(userRepository, never()).save(any());
        verify(emailVerificationTokenRepository, never()).save(any());
        verify(mailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    // === EMAIL VERIFICATION ===

    @Test
    void shouldVerifyEmailSuccessfully() {
        String token = UUID.randomUUID().toString();
        User user = TestUserFactory.createTestUser();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .createdAt(LocalDateTime.now())
                .used(false)
                .build();

        when(emailVerificationTokenRepository.findByToken(token)).thenReturn(Optional.of(verificationToken));
        when(userRepository.save(user)).thenReturn(user);
        when(tokenRepository.save(any())).thenReturn(new Token());

        AuthTokensResponse response = authService.verifyEmail(token);

        assertNotNull(response);
        verify(emailVerificationTokenRepository).findByToken(token);
        verify(userRepository).save(user);
        verify(tokenRepository).save(any());
    }

    @Test
    void shouldFailVerificationWithInvalidToken() {
        String token = "invalid-token";

        when(emailVerificationTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        InvalidTokenException exception = assertThrows(InvalidTokenException.class, () -> {
            authService.verifyEmail(token);
        });

        assertEquals("Invalid or missing token", exception.getMessage());
        verify(emailVerificationTokenRepository).findByToken(token);
    }

    @Test
    void shouldFailVerificationWithUsedToken() {
        String token = UUID.randomUUID().toString();
        User user = TestUserFactory.createTestUser();
        EmailVerificationToken usedToken = EmailVerificationToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .used(true) // token já foi usado
                .build();

        when(emailVerificationTokenRepository.findByToken(token)).thenReturn(Optional.of(usedToken));

        InvalidTokenException exception = assertThrows(InvalidTokenException.class, () -> {
            authService.verifyEmail(token);
        });

        assertEquals("Token has already been used", exception.getMessage());
        verify(emailVerificationTokenRepository).findByToken(token);
    }

    @Test
    void shouldFailVerificationWithUsedOrExpiredToken() {
        String token = UUID.randomUUID().toString();
        User user = TestUserFactory.createTestUser();
        EmailVerificationToken expiredToken = EmailVerificationToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().minusMinutes(1)) // expirado
                .createdAt(LocalDateTime.now())
                .used(false)
                .build();

        when(emailVerificationTokenRepository.findByToken(token)).thenReturn(Optional.of(expiredToken));

        InvalidTokenException exception = assertThrows(InvalidTokenException.class, () -> {
            authService.verifyEmail(token);
        });

        assertEquals("Token has expired", exception.getMessage());
        verify(emailVerificationTokenRepository).findByToken(token);
    }

    // === RESEND VERIFICATION ===

    @Test
    void shouldResendVerificationEmailSuccessfully() {
        String email = "test@example.com";
        User user = TestUserFactory.createTestUser();
        user.setEmail(email);
        user.setEmailVerifiedAt(null); // ainda não verificado

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Act
        authService.resendVerificationEmail(email);

        // Assert
        verify(userRepository).findByEmail(email);
        verify(emailVerificationTokenRepository).save(any());
        verify(mailService).sendVerificationEmail(eq(user.getEmail()), anyString(), anyString());
    }

    @Test
    void shouldFailResendVerificationIfUserNotFound() {
        String email = "nonexistent@example.com";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> {
            authService.resendVerificationEmail(email);
        });

        assertEquals("User not found with email: " + email, exception.getMessage());
        verify(userRepository).findByEmail(email);
        verifyNoMoreInteractions(emailVerificationTokenRepository, mailService);
    }

    @Test
    void shouldFailResendVerificationIfEmailAlreadyVerified() {
        String email = "already@verified.com";
        User user = TestUserFactory.createTestUser();
        user.setEmail(email);
        user.setEmailVerifiedAt(LocalDateTime.now()); // já verificado

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        InvalidTokenException exception = assertThrows(InvalidTokenException.class, () -> {
            authService.resendVerificationEmail(email);
        });

        assertEquals("Email is already verified", exception.getMessage());
        verify(userRepository).findByEmail(email);
        verifyNoMoreInteractions(emailVerificationTokenRepository, mailService);
    }

    // === LOGIN ===

    @Test
    void shouldLoginSuccessfullyWithValidCredentialsAndVerifiedEmail() {
        // Arrange
        String email = "valid@example.com";
        String password = "securePassword123";
        User user = TestUserFactory.createTestUser();
        user.setEmail(email);
        user.setPassword(password);
        user.setEmailVerifiedAt(LocalDateTime.now());

        var authToken = new UsernamePasswordAuthenticationToken(email, password);

        when(authenticationManager.authenticate(authToken)).thenReturn(authToken);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(eq(email), anyMap())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(email), anyMap())).thenReturn("refresh-token");

        // Act
        AuthTokensResponse response = authService.login(new LoginRequest(email, password));

        // Assert
        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());

        verify(authenticationManager).authenticate(authToken);
        verify(userRepository).findByEmail(email);
        verify(jwtService).generateAccessToken(eq(email), anyMap());
        verify(jwtService).generateRefreshToken(eq(email), anyMap());
        verify(tokenRepository).save(any());
    }

    @Test
    void shouldFailLoginIfEmailNotVerified() {
        // Arrange
        String email = "unverified@example.com";
        String password = "somePassword123";
        User user = TestUserFactory.createTestUser();
        user.setEmail(email);
        user.setPassword(password);
        user.setEmailVerifiedAt(null); // ainda não verificado

        var authToken = new UsernamePasswordAuthenticationToken(email, password);

        when(authenticationManager.authenticate(authToken)).thenReturn(authToken);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Act + Assert
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            authService.login(new LoginRequest(email, password));
        });

        assertEquals("Email not verified. Please check your inbox", exception.getMessage());

        verify(authenticationManager).authenticate(authToken);
        verify(userRepository).findByEmail(email);
    }

    @Test
    void shouldFailLoginWithBadCredentials() {
        // Arrange
        String email = "wrong@example.com";
        String password = "wrongPassword";
        var authToken = new UsernamePasswordAuthenticationToken(email, password);

        when(authenticationManager.authenticate(authToken))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // Act + Assert
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            authService.login(new LoginRequest(email, password));
        });

        assertEquals("Invalid email or password", exception.getMessage());

        verify(authenticationManager).authenticate(authToken);
        verifyNoInteractions(userRepository);
    }

    // === LOGOUT ===

    @Test
    void shouldRevokeTokenSuccessfullyOnLogout() {
        // Arrange
        String token = "Bearer dummy-token";
        Token userToken = Token.builder()
                .token("dummy-token")
                .revoked(false)
                .expired(false)
                .build();

        when(tokenRepository.findByToken("dummy-token"))
                .thenReturn(Optional.of(userToken));

        // Act
        authService.logout(token);

        // Assert
        assertTrue(userToken.isRevoked());
        assertTrue(userToken.isExpired());
        verify(tokenRepository).findByToken("dummy-token");
        verify(tokenRepository).save(userToken);
    }

    // === REFRESH TOKEN ===

    @Test
    void shouldRefreshTokenSuccessfully() {
        String refreshToken = "valid-refresh-token";
        String accessToken = "old-access-token";
        String email = "test@example.com";

        User user = TestUserFactory.createTestUser();
        Token validToken = Token.builder()
                .token(refreshToken)
                .user(user)
                .expired(false)
                .revoked(false)
                .build();

        when(jwtService.extractEmail(refreshToken)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(refreshToken, email)).thenReturn(true);
        when(tokenRepository.findByToken(refreshToken)).thenReturn(Optional.of(validToken));
        when(jwtService.generateAccessToken(eq(email), anyMap())).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(eq(email), anyMap())).thenReturn("new-refresh-token");

        AuthTokensResponse response = authService.refreshToken(accessToken, refreshToken);

        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());

        verify(jwtService).extractEmail(refreshToken);
        verify(userRepository).findByEmail(email);
        verify(jwtService).isTokenValid(refreshToken, email);
        verify(tokenRepository).findByToken(refreshToken);
        verify(tokenRepository, times(2)).save(any(Token.class));
    }

    @Test
    void shouldFailRefreshIfUserNotFound() {
        String refreshToken = "dummy-refresh-token";
        String bearerRefreshToken = "Bearer " + refreshToken;
        String email = "ghost@example.com";

        when(jwtService.extractEmail(bearerRefreshToken)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> {
            authService.refreshToken("dummy-access-token", bearerRefreshToken);
        });

        assertEquals("User not found with email: ghost@example.com", ex.getMessage());
        verify(jwtService).extractEmail(bearerRefreshToken);
        verify(userRepository).findByEmail(email);
    }

    @Test
    void shouldFailRefreshIfTokenInvalidOrRevoked() {
        String refreshToken = "revoked-token";
        String accessToken = "any-access-token";
        String email = "test@example.com";

        User user = TestUserFactory.createTestUser();

        when(jwtService.extractEmail(refreshToken)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(refreshToken, email)).thenReturn(false);

        InvalidTokenException ex = assertThrows(InvalidTokenException.class, () -> {
            authService.refreshToken(accessToken, refreshToken);
        });

        assertEquals("Invalid or expired refresh token", ex.getMessage());

        verify(jwtService).extractEmail(refreshToken);
        verify(userRepository).findByEmail(email);
        verify(jwtService).isTokenValid(refreshToken, email);
    }

    // === ME ===

    @Test
    void shouldReturnAuthenticatedUserSuccessfully() {}

    @Test
    void shouldFailIfUserNotFoundInSecurityContext() {}

    // === FORGOT PASSWORD ===

    @Test
    void shouldSendForgotPasswordEmailSuccessfully() {
        String email = "test@example.com";
        String name = "Test User";
        User user = TestUserFactory.createTestUser();
        user.setEmail(email);
        user.setName(name);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        authService.forgotPassword(email);

        verify(userRepository).findByEmail(email);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(mailService).sendResetPasswordEmail(eq(email), eq(name), anyString());
    }

    @Test
    void shouldFailForgotPasswordIfUserNotFound() {
        String email = "nonexistent@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> {
            authService.forgotPassword(email);
        });

        assertEquals("User not found with email: nonexistent@example.com", ex.getMessage());
        verify(userRepository).findByEmail(email);
        verifyNoInteractions(passwordResetTokenRepository, mailService);
    }

    // === RESET PASSWORD ===

    @Test
    void shouldResetPasswordSuccessfullyWithValidToken() {
        String email = "test@example.com";
        String token = "valid-reset-token";
        String newPassword = "newPassword123";

        User user = TestUserFactory.createTestUser();

        PasswordResetToken resetToken = new PasswordResetToken(
                null,
                token,
                user,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1),
                false
        );

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(resetToken));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        authService.resetPassword(token, newPassword);

        verify(passwordResetTokenRepository).findByToken(token);
        verify(userRepository).findByEmail(email);
        verify(userRepository).save(user);
    }

    @Test
    void shouldFailResetPasswordWithInvalidToken() {
        String token = "invalid-token";
        String newPassword = "newPassword123";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        InvalidTokenException ex = assertThrows(InvalidTokenException.class, () -> {
            authService.resetPassword(token, newPassword);
        });

        assertEquals("Invalid or missing reset token", ex.getMessage());
    }

    @Test
    void shouldFailResetPasswordWithExpiredToken() {
        String email = "test@example.com";
        String token = "expired-reset-token";
        String newPassword = "newPassword123";

        User user = TestUserFactory.createTestUser();

        PasswordResetToken expiredToken = new PasswordResetToken(
                null,
                token,
                user,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusMinutes(30),
                false
        );

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(expiredToken));

        InvalidTokenException exception = assertThrows(InvalidTokenException.class, () -> {
            authService.resetPassword(token, newPassword);
        });

        assertEquals("Reset token has expired", exception.getMessage());

        verify(passwordResetTokenRepository).findByToken(token);
        verify(userRepository, never()).findByEmail(email);
        verify(userRepository, never()).save(any(User.class));
    }

}
