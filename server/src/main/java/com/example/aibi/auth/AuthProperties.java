package com.example.aibi.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record AuthProperties(int sessionHours) {
    public AuthProperties {
        if (sessionHours <= 0) {
            sessionHours = 12;
        }
    }
}
