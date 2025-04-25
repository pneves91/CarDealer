package com.cardealer.services.config;

import com.cardealer.models.EmailVerificationToken;
import com.cardealer.models.PasswordResetToken;
import com.cardealer.models.Role;
import com.cardealer.models.User;
import com.cardealer.repositories.EmailVerificationTokenRepository;
import com.cardealer.repositories.PasswordResetTokenRepository;
import com.cardealer.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SeedConfig {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner initAdminUser() {
        return args -> {
            if (userRepository.findByEmail("admin@admin.com").isEmpty()) {
                User admin = User.builder()
                        .name("Admin")
                        .email("admin@admin.com")
                        .password(passwordEncoder.encode("admin123"))
                        .role(Role.ADMIN)
                        .enabled(true)
                        .emailVerifiedAt(LocalDateTime.now())
                        .build();
                userRepository.save(admin);
            }

            // Inserir tokens de redefinição de senha para testes
            userRepository.findByEmail("admin@admin.com").ifPresent(admin -> {
                // Remover tokens existentes
                passwordResetTokenRepository.findAllByUser(admin).forEach(passwordResetTokenRepository::delete);

                // Token já utilizado
                PasswordResetToken usedToken = PasswordResetToken.builder()
                        .token("used-reset-token-123")
                        .user(admin)
                        .used(true)
                        .expiresAt(LocalDateTime.now().plusHours(1))
                        .build();
                passwordResetTokenRepository.save(usedToken);

                // Token expirado
                PasswordResetToken expiredToken = PasswordResetToken.builder()
                        .token("expired-reset-token-456")
                        .user(admin)
                        .used(false)
                        .expiresAt(LocalDateTime.now().minusHours(1))
                        .build();
                passwordResetTokenRepository.save(expiredToken);
            });
        };
    }

    @Bean
    CommandLineRunner insertTestEmailTokens() {
        return args -> {
            // Criar utilizador de teste se não existir
            User user = userRepository.findByEmail("test.token@email.com")
                    .orElseGet(() -> {
                        User newUser = User.builder()
                                .name("Token Tester")
                                .email("test.token@email.com")
                                .password(passwordEncoder.encode("Password123"))
                                .role(Role.USER)
                                .enabled(true)
                                .build();
                        return userRepository.save(newUser);
                    });

            // Remover tokens anteriores para evitar conflitos
            List<EmailVerificationToken> existingTokens = emailVerificationTokenRepository.findAllByUser(user);
            emailVerificationTokenRepository.deleteAll(existingTokens);

            // Criar token já usado
            EmailVerificationToken usedToken = EmailVerificationToken.builder()
                    .token("used-token-123")
                    .user(user)
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .used(true)
                    .build();

            // Criar token expirado
            EmailVerificationToken expiredToken = EmailVerificationToken.builder()
                    .token("expired-token-456")
                    .user(user)
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .expiresAt(LocalDateTime.now().minusHours(1))
                    .used(false)
                    .build();

            emailVerificationTokenRepository.save(usedToken);
            emailVerificationTokenRepository.save(expiredToken);
        };
    }
}
