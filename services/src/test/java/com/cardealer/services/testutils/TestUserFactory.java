package com.cardealer.services.testutils;

import com.cardealer.models.Role;
import com.cardealer.models.User;

import java.time.LocalDateTime;

public class TestUserFactory {
    public static User createTestUser() {
        return User.builder()
                .id(1L)
                .name("Test User")
                .email("test@example.com")
                .password("hashedPassword")
                .role(Role.USER)
                .enabled(true)
                .emailVerifiedAt(LocalDateTime.now())
                .build();
    }
}
