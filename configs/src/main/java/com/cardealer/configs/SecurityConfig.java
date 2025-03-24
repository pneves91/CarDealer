package com.cardealer.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/auth/register", "/users"))  // Ignora CSRF para esse endpoint
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/auth/register", "/users").permitAll()  // Permite a rota
                        .anyRequest().authenticated());
        return http.build();
    }
}