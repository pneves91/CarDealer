package com.cardealer.services.mail;

import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailTemplateService {

    public String buildEmailVerificationTemplate(String name, String verificationLink) {
        String template = """
            <html>
            <body>
                <p>Hi ${name},</p>
                <p>Thank you for registering. Please verify your email by clicking the link below:</p>
                <p><a href="${link}">Verify Email</a></p>
                <p>If you did not request this, you can safely ignore this email.</p>
                <p>Best regards,<br/>CarDealer Team</p>
            </body>
            </html>
            """;

        Map<String, String> values = Map.of(
                "name", name,
                "link", verificationLink
        );

        return StringSubstitutor.replace(template, values, "${", "}");
    }

    public String buildResetPasswordTemplate(String name, String resetLink) {
        String template = """
            <html>
            <body>
                <p>Hi ${name},</p>
                <p>You have requested to reset your password. Click the link below to proceed:</p>
                <p><a href="${link}">Reset Password</a></p>
                <p>If you did not request this, you can safely ignore this email.</p>
                <p>Best regards,<br/>CarDealer Team</p>
            </body>
            </html>
            """;

        Map<String, String> values = Map.of(
                "name", name,
                "link", resetLink
        );

        return StringSubstitutor.replace(template, values, "${", "}");
    }
}
