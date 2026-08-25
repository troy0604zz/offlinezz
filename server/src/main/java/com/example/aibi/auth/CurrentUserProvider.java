package com.example.aibi.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {
    public AuthenticatedUser user() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    public String username() {
        AuthenticatedUser user = user();
        return user == null ? "system" : user.profile().username();
    }

    public long userId() {
        AuthenticatedUser user = user();
        return user == null ? 0 : user.profile().id();
    }
}
