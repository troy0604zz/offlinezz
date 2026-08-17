package com.example.aibi.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.Collection;

public record AuthenticatedUser(UserProfile profile) implements Principal {
    @Override
    public String getName() {
        return profile.username();
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return profile.permissions().stream().map(SimpleGrantedAuthority::new).toList();
    }
}
