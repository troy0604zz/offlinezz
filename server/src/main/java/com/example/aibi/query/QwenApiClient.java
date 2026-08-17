package com.example.aibi.query;

import com.example.aibi.config.AiBiProperties;
import com.example.aibi.config.ModelRuntimeService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "real")
public class QwenApiClient {
    private final AiBiProperties properties;
    private final ModelRuntimeService runtime;
    private final RestClient client;

    public QwenApiClient(AiBiProperties properties, ModelRuntimeService runtime, RestClient.Builder builder) {
        this.properties = properties;
        this.runtime = runtime;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Math.max(1, properties.ai().qwenApi().timeoutSeconds()) * 1000;
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.client = builder.clone()
                .baseUrl(properties.ai().qwenApi().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.ai().qwenApi().apiKey())
                .requestFactory(factory)
                .build();
    }

    public String chat(String model, String system, String user) {
        runtime.requireQwenApiKey();
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "temperature", 0.1,
                "enable_thinking", false,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
        JsonNode response = client.post().uri("/chat/completions").body(body).retrieve().body(JsonNode.class);
        if (response == null) throw new IllegalStateException("千问官方 API 返回了空响应");
        String content = response.path("choices").path(0).path("message").path("content").asText();
        if (content.isBlank()) throw new IllegalStateException("千问官方 API 未返回有效内容");
        return content;
    }

    public List<List<Double>> embeddings(String model, int dimensions, List<String> input) {
        runtime.requireQwenApiKey();
        List<List<Double>> result = new ArrayList<>();
        for (int start = 0; start < input.size(); start += 20) {
            List<String> batch = input.subList(start, Math.min(start + 20, input.size()));
            Map<String, Object> body = Map.of("model", model, "input", batch,
                    "dimensions", dimensions, "encoding_format", "float");
            JsonNode response = client.post().uri("/embeddings").body(body).retrieve().body(JsonNode.class);
            if (response == null || !response.path("data").isArray()) {
                throw new IllegalStateException("千问官方向量 API 返回格式无效");
            }
            List<JsonNode> ordered = new ArrayList<>();
            response.path("data").forEach(ordered::add);
            ordered.sort(Comparator.comparingInt(node -> node.path("index").asInt()));
            for (JsonNode item : ordered) {
                List<Double> vector = new ArrayList<>();
                item.path("embedding").forEach(value -> vector.add(value.asDouble()));
                if (vector.size() != dimensions) {
                    throw new IllegalStateException("千问官方向量维度不符合配置：" + vector.size());
                }
                result.add(vector);
            }
        }
        return result;
    }
}
