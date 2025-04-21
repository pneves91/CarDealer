package com.cardealer.services;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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

        mailSender.send(message);
    }

    public void sendPasswordResetEmail(String to, String resetLink) {
        String subject = "Reset your password";
        String text = String.format(
                "To reset your password, click the following link:\n\n%s", resetLink);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        mailSender.send(message);
    }

}
