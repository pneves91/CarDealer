package com.cardealer.services;

import com.cardealer.configs.properties.AppProperties;
import com.cardealer.mappers.UserMapper;
import com.cardealer.models.EmailVerificationToken;
import com.cardealer.models.PasswordResetToken;
import com.cardealer.models.Token;
import com.cardealer.models.User;
import com.cardealer.models.dto.UserResponseDTO;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;
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
        verify(tokenRepository, times(2)).save(any());
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
        verify(tokenRepository, times(2)).save(any());
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
        verify(tokenRepository, times(2)).findByToken("dummy-token");
        verify(tokenRepository).save(userToken);
    }

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

    // === REFRESH TOKEN ===

    @Test
    void shouldRefreshTokenSuccessfully() {
        String refreshToken = "valid-refresh-token";
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

        AuthTokensResponse response = authService.refreshToken(refreshToken);

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
        String email = "ghost@example.com";

        when(jwtService.extractEmail(refreshToken)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> {
            authService.refreshToken(refreshToken);
        });

        assertEquals("User not found with email: ghost@example.com", ex.getMessage());
        verify(jwtService).extractEmail(refreshToken);
        verify(userRepository).findByEmail(email);
    }

    @Test
    void shouldFailRefreshIfTokenInvalidOrRevoked() {
        String refreshToken = "revoked-token";
        String email = "test@example.com";

        User user = TestUserFactory.createTestUser();

        when(jwtService.extractEmail(refreshToken)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(refreshToken, email)).thenReturn(false);

        InvalidTokenException ex = assertThrows(InvalidTokenException.class, () -> {
            authService.refreshToken(refreshToken);
        });

        assertEquals("Invalid or expired refresh token", ex.getMessage());

        verify(jwtService).extractEmail(refreshToken);
        verify(userRepository).findByEmail(email);
        verify(jwtService).isTokenValid(refreshToken, email);
    }

    // === ME ===

    @Test
    void shouldReturnAuthenticatedUserSuccessfully() {
        // Arrange
        String email = "user@user.com";

        User mockUser = TestUserFactory.createTestUser();
        mockUser.setEmail(email);

        UserResponseDTO expectedDto = new UserResponseDTO(
                mockUser.getId(),
                mockUser.getName(),
                mockUser.getEmail(),
                mockUser.getRole().name()
        );

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(email);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(userMapper.toResponse(mockUser)).thenReturn(expectedDto);

        // Act
        UserResponseDTO result = authService.getAuthenticatedUser();

        // Assert
        assertNotNull(result);
        assertEquals(expectedDto.getId(), result.getId());
        assertEquals(expectedDto.getName(), result.getName());
        assertEquals(expectedDto.getEmail(), result.getEmail());
        assertEquals(expectedDto.getRole(), result.getRole());
    }

    @Test
    void shouldFailIfAuthenticatedUserNotFoundInDatabase() {
        // Arrange
        String email = "nonexistent@user.com";

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(email);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> {
            authService.getAuthenticatedUser();
        });

        assertEquals("User not found with email: " + email, exception.getMessage());
    }

    @Test
    void shouldFailIfUserNotAuthenticated() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Act & Assert
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            authService.getAuthenticatedUser();
        });

        assertEquals("Access denied. Please authenticate", exception.getMessage());
    }

    @Test
    void shouldFailIfAuthenticationIsNull() {
        // Arrange
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        // Act & Assert
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            authService.getAuthenticatedUser();
        });

        assertEquals("Access denied. Please authenticate", exception.getMessage());
    }

    @Test
    void shouldFailIfAuthenticationIsAnonymous() {
        // Arrange
        Authentication anonymousAuth = mock(AnonymousAuthenticationToken.class);
        when(anonymousAuth.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(anonymousAuth);
        SecurityContextHolder.setContext(securityContext);

        // Act & Assert
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            authService.getAuthenticatedUser();
        });

        assertEquals("Access denied. Please authenticate", exception.getMessage());
    }

    // === SINGLE-SESSION ===

    @Test
    void shouldRevokeOldTokensIfSingleSessionEnabled() {
        // Arrange
        String email = "user@example.com";
        String accessToken = "new-access-token";
        String refreshToken = "new-refresh-token";
        String sessionId = "session-123";

        User user = TestUserFactory.createTestUser();
        user.setEmail(email);

        List<Token> existingTokens = List.of(
                Token.builder().token("old-token-1").user(user).revoked(false).expired(false).build(),
                Token.builder().token("old-token-2").user(user).revoked(false).expired(false).build()
        );

        when(appProperties.isSingleSession()).thenReturn(true);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(eq(email), any())).thenReturn(accessToken);
        when(jwtService.generateRefreshToken(eq(email), any())).thenReturn(refreshToken);
        when(tokenRepository.findAllByUser(user)).thenReturn(existingTokens);

        LoginRequest loginRequest = new LoginRequest(email, "password");

        // Act
        authService.login(loginRequest);

        // Assert
        verify(tokenRepository, times(1)).saveAll(argThat(tokens ->
                StreamSupport.stream(tokens.spliterator(), false)
                        .allMatch(t -> t.isRevoked() && t.isExpired())
        ));


        verify(tokenRepository, times(2)).save(any(Token.class));
    }

    @Test
    void shouldKeepPreviousTokensIfSingleSessionDisabled() {
        // Arrange
        String email = "user@example.com";
        String accessToken = "new-access-token";
        String refreshToken = "new-refresh-token";

        User user = TestUserFactory.createTestUser();
        user.setEmail(email);

        List<Token> existingTokens = List.of(
                Token.builder().token("old-token-1").user(user).revoked(false).expired(false).build(),
                Token.builder().token("old-token-2").user(user).revoked(false).expired(false).build()
        );

        when(appProperties.isSingleSession()).thenReturn(false);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(eq(email), any())).thenReturn(accessToken);
        when(jwtService.generateRefreshToken(eq(email), any())).thenReturn(refreshToken);

        // lenient para evitar UnnecessaryStubbingException
        lenient().when(tokenRepository.findAllByUser(user)).thenReturn(existingTokens);

        LoginRequest loginRequest = new LoginRequest(email, "password");

        // Act
        authService.login(loginRequest);

        // Assert
        verify(tokenRepository, never()).saveAll(argThat(tokens ->
                StreamSupport.stream(tokens.spliterator(), false)
                        .anyMatch(t -> t.isRevoked() || t.isExpired())
        ));

        verify(tokenRepository, times(2)).save(any(Token.class));
    }

    @Test
    void shouldRevokeOnlyCurrentSessionTokensOnLogout() {
        // Arrange
        String tokenValue = "access-token-to-logout";
        String sessionId = "session-abc";

        User user = TestUserFactory.createTestUser();

        Token currentToken = Token.builder()
                .token(tokenValue)
                .user(user)
                .sessionId(sessionId)
                .revoked(false)
                .expired(false)
                .build();

        List<Token> allUserTokens = List.of(
                currentToken,
                Token.builder().token("refresh-of-session-abc").user(user).sessionId(sessionId).revoked(false).expired(false).build(),
                Token.builder().token("token-from-other-session").user(user).sessionId("session-other").revoked(false).expired(false).build()
        );

        when(appProperties.isSingleSession()).thenReturn(false);
        when(tokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(currentToken));
        when(tokenRepository.findAllByUser(user)).thenReturn(allUserTokens);

        String header = "Bearer " + tokenValue;

        // Act
        authService.logout(header);

        // Assert
        verify(tokenRepository).save(currentToken);

        verify(tokenRepository).saveAll(argThat(tokens ->
                StreamSupport.stream(tokens.spliterator(), false)
                        .allMatch(t -> sessionId.equals(t.getSessionId()) && t.isRevoked() && t.isExpired())
        ));
    }

    @Test
    void shouldRevokeRefreshTokenAfterUse() {
        // Arrange
        String email = "user@example.com";
        String refreshTokenValue = "valid-refresh-token";
        String newAccessToken = "new-access-token";
        String newRefreshToken = "new-refresh-token";

        User user = TestUserFactory.createTestUser();
        user.setEmail(email);

        Token storedRefreshToken = Token.builder()
                .token(refreshTokenValue)
                .user(user)
                .revoked(false)
                .expired(false)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtService.extractEmail(refreshTokenValue)).thenReturn(email);
        when(jwtService.isTokenValid(refreshTokenValue, email)).thenReturn(true);
        when(tokenRepository.findByToken(refreshTokenValue)).thenReturn(Optional.of(storedRefreshToken));
        when(jwtService.generateAccessToken(eq(email), any())).thenReturn(newAccessToken);
        when(jwtService.generateRefreshToken(eq(email), any())).thenReturn(newRefreshToken);
        when(appProperties.isSingleSession()).thenReturn(false);

        // Act
        AuthTokensResponse response = authService.refreshToken(refreshTokenValue);

        // Assert
        assertEquals(newAccessToken, response.getAccessToken());
        assertEquals(newRefreshToken, response.getRefreshToken());

        assertTrue(storedRefreshToken.isRevoked());
        assertTrue(storedRefreshToken.isExpired());

        verify(tokenRepository).save(storedRefreshToken);
        verify(tokenRepository, times(3)).save(any(Token.class));
    }

    @Test
    void shouldRejectRefreshTokenIfAlreadyUsed() {
        // Arrange
        String email = "user@example.com";
        String refreshTokenValue = "used-refresh-token";

        User user = TestUserFactory.createTestUser();
        user.setEmail(email);

        Token storedRefreshToken = Token.builder()
                .token(refreshTokenValue)
                .user(user)
                .revoked(true)
                .expired(true)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtService.extractEmail(refreshTokenValue)).thenReturn(email);
        when(jwtService.isTokenValid(refreshTokenValue, email)).thenReturn(true);
        when(tokenRepository.findByToken(refreshTokenValue)).thenReturn(Optional.of(storedRefreshToken));

        // Act + Assert
        InvalidTokenException ex = assertThrows(
                InvalidTokenException.class,
                () -> authService.refreshToken(refreshTokenValue)
        );

        assertEquals("Invalid or expired refresh token", ex.getMessage());
    }

}
