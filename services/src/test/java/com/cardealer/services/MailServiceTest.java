package com.cardealer.services;

import com.cardealer.services.mail.EmailTemplateService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private MimeMessage mimeMessage;

    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailService = new MailService(mailSender);

        // AQUI: injetamos o mock do EmailTemplateService via Reflection
        ReflectionTestUtils.setField(mailService, "emailTemplateService", emailTemplateService);

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void shouldSendVerificationEmailSuccessfully() throws MessagingException {
        // Arrange
        when(emailTemplateService.buildEmailVerificationTemplate(anyString(), anyString()))
                .thenReturn("<html>Verification Email</html>");

        // Act & Assert
        assertDoesNotThrow(() ->
                mailService.sendVerificationEmail(
                        "user@example.com",
                        "Test User",
                        "http://localhost:8080/verify"
                )
        );

        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void shouldSendPasswordResetEmailSuccessfully() throws MessagingException {
        // Arrange
        when(emailTemplateService.buildResetPasswordTemplate(anyString(), anyString()))
                .thenReturn("<html>Reset Password Email</html>");

        // Act & Assert
        assertDoesNotThrow(() ->
                mailService.sendResetPasswordEmail(
                        "user@example.com",
                        "Test User",
                        "http://localhost:8080/reset-password"
                )
        );

        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void shouldThrowExceptionIfEmailSendingFails() {
        // Arrange
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("Simulated error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                mailService.sendVerificationEmail(
                        "fail@example.com",
                        "Test User",
                        "http://localhost:8080/verify"
                )
        );
    }
}
