package com.example.aibi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AiBiProperties(Ai ai, Query query, Storage storage) {
    public record Ai(String mode, String chatProvider, String embeddingProvider,
                     Ollama ollama, QwenApi qwenApi, Qdrant qdrant) {}
    public record Ollama(String baseUrl, String chatModel, String embeddingModel, int contextLength, int timeoutSeconds) {}
    public record QwenApi(String baseUrl, String apiKey, String chatModel, String embeddingModel,
                          int embeddingDimensions, int timeoutSeconds) {}
    public record Qdrant(String baseUrl, String apiKey, String collectionPrefix, int vectorSize) {}
    public record Query(int defaultLimit, int maxLimit, int timeoutSeconds) {}
    public record Storage(String root) {}
}
