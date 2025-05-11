package com.cardealer.services;

import com.cardealer.services.mail.EmailTemplateService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Autowired
    private EmailTemplateService emailTemplateService;

    public void sendVerificationEmail(String to, String name, String verificationLink) {
        // Envia email de verificação de conta com link personalizado
        String subject = "Verify your email address";
        String content = emailTemplateService.buildEmailVerificationTemplate(name, verificationLink);
        sendHtmlEmail(to, subject, content);
        log.info("Verification email triggered for {}", to);
    }

    public void sendResetPasswordEmail(String to, String name, String resetLink) {
        // Envia email de recuperação de password com link de reset
        String subject = "Reset your password";
        String content = emailTemplateService.buildResetPasswordTemplate(name, resetLink);
        sendHtmlEmail(to, subject, content);
        log.info("Password reset email triggered for {}", to);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        // Envia email em formato HTML com subject e conteúdo fornecido
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML content

            mailSender.send(message);

            log.info("HTML email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send HTML email to {}. Error: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send HTML email", e);
        }
    }
}
