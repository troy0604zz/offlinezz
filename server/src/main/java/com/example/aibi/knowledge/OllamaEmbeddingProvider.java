package com.example.aibi.knowledge;

import com.example.aibi.config.AiBiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "real")
public class OllamaEmbeddingProvider implements EmbeddingProvider {
    private final AiBiProperties properties;
    private final RestClient client;

    public OllamaEmbeddingProvider(AiBiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.clone().baseUrl(properties.ai().ollama().baseUrl()).build();
    }

    @Override
    public List<List<Double>> embeddings(List<String> input) {
        JsonNode response = client.post().uri("/api/embed")
                .body(Map.of("model", modelName(), "input", input))
                .retrieve().body(JsonNode.class);
        if (response == null || !response.path("embeddings").isArray()) {
            throw new IllegalStateException("Ollama 向量响应格式无效");
        }
        List<List<Double>> result = new ArrayList<>();
        for (JsonNode embedding : response.path("embeddings")) {
            List<Double> vector = new ArrayList<>();
            embedding.forEach(value -> vector.add(value.asDouble()));
            result.add(vector);
        }
        return result;
    }

    @Override public String providerName() { return "ollama"; }
    @Override public String modelName() { return properties.ai().ollama().embeddingModel(); }
    @Override public int vectorSize() { return properties.ai().qdrant().vectorSize(); }
}
