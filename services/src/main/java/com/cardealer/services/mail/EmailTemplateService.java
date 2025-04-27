package com.cardealer.services.mail;

import org.apache.commons.text.StringSubstitutor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class EmailTemplateService {

    private String loadTemplate(String templateName) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/" + templateName);
            byte[] data = resource.getInputStream().readAllBytes();
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load email template: " + templateName, e);
        }
    }

    public String buildEmailVerificationTemplate(String name, String verificationLink) {
        String template = loadTemplate("verification-email.html");

        Map<String, String> values = Map.of(
                "name", name,
                "link", verificationLink
        );

        return StringSubstitutor.replace(template, values, "${", "}");
    }

    public String buildResetPasswordTemplate(String name, String resetLink) {
        String template = loadTemplate("reset-password-email.html");

        Map<String, String> values = Map.of(
                "name", name,
                "link", resetLink
        );

        return StringSubstitutor.replace(template, values, "${", "}");
    }
}
