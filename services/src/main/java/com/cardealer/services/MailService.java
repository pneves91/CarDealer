package com.cardealer.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    public void sendVerificationEmail(String to, String verificationLink) {
        String subject = "Verify your email";
        String text = String.format(
                "Please click the following link to verify your email:\n\n%s", verificationLink);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        try {
            log.info("Enviando email de verificação para {}", to);
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Erro ao enviar email de verificação para {}: {}", to, e.getMessage());
        }
    }

    public void sendPasswordResetEmail(String to, String resetLink) {
        String subject = "Reset your password";
        String text = String.format(
                "To reset your password, click the following link:\n\n%s", resetLink);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        try {
            log.info("Enviando email de redefinição para {}", to);
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Erro ao enviar email de redefinição para {}: {}", to, e.getMessage());
        }
    }

}
