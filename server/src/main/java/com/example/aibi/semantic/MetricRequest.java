package com.example.aibi.semantic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MetricRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 2000) String expressionSql,
        @NotBlank @Size(max = 200) String baseTable) {}

