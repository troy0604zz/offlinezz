package com.example.aibi.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(@NotBlank @Size(max = 2000) String question, String knowledgeDomain) {
    public String domainOrDefault() { return knowledgeDomain == null || knowledgeDomain.isBlank() ? "sales" : knowledgeDomain; }
}

