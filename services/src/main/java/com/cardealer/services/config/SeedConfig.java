package com.cardealer.services.config;

import com.cardealer.models.EmailVerificationToken;
import com.cardealer.models.PasswordResetToken;
import com.cardealer.models.Role;
import com.cardealer.models.User;
import com.cardealer.repositories.EmailVerificationTokenRepository;
import com.cardealer.repositories.PasswordResetTokenRepository;
import com.cardealer.repositories.UserRepository;
import com.cardealer.services.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.Key;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;

@Configuration
@Profile("dev")
@RequiredArgsConstructor
public class SeedConfig {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private static final Key TEMPORARY_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);

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

            userRepository.findByEmail("admin@admin.com").ifPresent(admin -> {
                passwordResetTokenRepository.findAllByUser(admin).forEach(passwordResetTokenRepository::delete);

                PasswordResetToken usedToken = PasswordResetToken.builder()
                        .token("used-reset-token-123")
                        .user(admin)
                        .used(true)
                        .expiresAt(LocalDateTime.now().plusHours(1))
                        .build();
                passwordResetTokenRepository.save(usedToken);

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

            emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findAllByUser(user));

            EmailVerificationToken usedToken = EmailVerificationToken.builder()
                    .token("used-token-123")
                    .user(user)
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .used(true)
                    .build();

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

    @Bean
    CommandLineRunner insertTestTokensForAuthMe() {
        return args -> {
            // Criar User com token expirado
            User expiredUser = userRepository.findByEmail("expired.token@email.com")
                    .orElseGet(() -> {
                        User newUser = User.builder()
                                .name("Expired User")
                                .email("expired.token@email.com")
                                .password(passwordEncoder.encode("Password123"))
                                .role(Role.USER)
                                .enabled(true)
                                .build();
                        return userRepository.save(newUser);
                    });

            // Gerar manualmente o token expirado
            String expiredToken = Jwts.builder()
                    .setSubject(expiredUser.getEmail())
                    .setIssuedAt(new Date(System.currentTimeMillis() - 3600000)) // Emitido há 1 hora
                    .setExpiration(new Date(System.currentTimeMillis() - 1800000)) // Expirado há 30 minutos
                    .signWith(jwtService.getKey(), SignatureAlgorithm.HS256)
                    .compact();

            System.out.println("🔴 Expired JWT Token:");
            System.out.println(expiredToken);

            // Criar User para token de ghost user
            User ghostUser = User.builder()
                    .name("Ghost User")
                    .email("ghost.user@email.com")
                    .password(passwordEncoder.encode("Password123"))
                    .role(Role.USER)
                    .enabled(true)
                    .build();
            ghostUser = userRepository.save(ghostUser);

            String ghostToken = jwtService.generateAccessToken(ghostUser.getEmail(), new HashMap<>());

            userRepository.delete(ghostUser);

            System.out.println("👻 Token for Deleted User:");
            System.out.println(ghostToken);
        };
    }
}
