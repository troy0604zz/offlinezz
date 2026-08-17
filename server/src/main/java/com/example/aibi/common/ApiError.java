package com.example.aibi.common;

import java.time.Instant;

public record ApiError(String code, String message, String traceId, Instant timestamp) {
    public static ApiError of(String code, String message, String traceId) {
        return new ApiError(code, message, traceId, Instant.now());
    }
}

