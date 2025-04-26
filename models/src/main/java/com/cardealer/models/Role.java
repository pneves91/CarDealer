package com.cardealer.models;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public enum Role {
    USER(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))),
    ADMIN(Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

    private final List<GrantedAuthority> authorities;

    public List<GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
