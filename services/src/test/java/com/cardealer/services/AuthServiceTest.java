package com.cardealer.services;

import com.cardealer.configs.properties.AppProperties;
import com.cardealer.models.EmailVerificationToken;
import com.cardealer.models.User;
import com.cardealer.repositories.EmailVerificationTokenRepository;
import com.cardealer.repositories.UserRepository;
import com.cardealer.services.security.JwtService;
import com.cardealer.services.MailService;
import com.cardealer.models.request.auth.RegisterRequest;
import com.cardealer.models.response.auth.RegisterResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

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

    @InjectMocks
    private AuthService authService;

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
}
