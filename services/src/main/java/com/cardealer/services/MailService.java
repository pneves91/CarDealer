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
        String subject = "Verify your email address";
        String content = emailTemplateService.buildEmailVerificationTemplate(name, verificationLink);
        sendHtmlEmail(to, subject, content);
    }

    public void sendResetPasswordEmail(String to, String name, String resetLink) {
        String subject = "Reset your password";
        String content = emailTemplateService.buildResetPasswordTemplate(name, resetLink);
        sendHtmlEmail(to, subject, content);
    }

    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML

            mailSender.send(message);

            log.info("HTML email sent to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send HTML email to {}", to, e);
            throw new RuntimeException("Failed to send HTML email", e);
        }
    }

}
