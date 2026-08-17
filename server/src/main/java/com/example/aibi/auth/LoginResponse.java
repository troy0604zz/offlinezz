package com.example.aibi.auth;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserProfile user) {
}
