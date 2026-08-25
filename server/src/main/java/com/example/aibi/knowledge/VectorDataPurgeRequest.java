package com.example.aibi.knowledge;

import jakarta.validation.constraints.NotBlank;

public record VectorDataPurgeRequest(
        String domain,
        String assetType,
        @NotBlank String confirmation
) {
}
