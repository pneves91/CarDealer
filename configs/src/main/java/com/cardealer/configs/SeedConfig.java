package com.cardealer.configs;

import com.cardealer.models.Role;
import com.cardealer.models.User;
import com.cardealer.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class SeedConfig {

    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner seedUsers(UserRepository userRepository) {
        return args -> {
            if (userRepository.findByEmail("admin@admin.com").isEmpty()) {
                User admin = User.builder()
                        .name("Admin")
                        .email("admin@admin.com")
                        .password(passwordEncoder.encode("123456"))
                        .role(Role.ADMIN)
                        .enabled(true)
                        .build();

                userRepository.save(admin);
                System.out.println("🟢 Utilizador ADMIN criado com sucesso.");
            }
        };
    }
}
