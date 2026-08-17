package com.example.aibi.query;

import com.example.aibi.config.AiBiProperties;
import com.example.aibi.knowledge.KnowledgeChunk;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "real")
public class OllamaLlmProvider implements LlmProvider {
    private final AiBiProperties properties;
    private final RestClient client;
    private final GeneratedQueryParser parser;
    private final SqlPromptFactory prompts;

    public OllamaLlmProvider(AiBiProperties properties, RestClient.Builder builder, GeneratedQueryParser parser,
                             SqlPromptFactory prompts) {
        this.properties = properties;
        this.client = builder.clone().baseUrl(properties.ai().ollama().baseUrl()).build();
        this.parser = parser;
        this.prompts = prompts;
    }

    @Override
    public GeneratedQuery generateSql(String knowledgeDomain, String question, List<KnowledgeChunk> context,
                                      List<Map<String, Object>> metrics, List<Map<String, Object>> relations) {
        SqlPromptFactory.SqlPrompt prompt = prompts.create(knowledgeDomain, question, context, metrics, relations);
        if (prompt.directMatch() != null) return prompt.directMatch();

        Map<String, Object> body = Map.of(
                "model", properties.ai().ollama().chatModel(),
                "stream", false,
                "format", "json",
                "think", false,
                "messages", List.of(Map.of("role", "system", "content", prompt.system()),
                        Map.of("role", "user", "content", prompt.user())),
                "options", Map.of("temperature", 0.1,
                        "num_ctx", properties.ai().ollama().contextLength()));
        JsonNode response = client.post().uri("/api/chat").body(body).retrieve().body(JsonNode.class);
        if (response == null) throw new IllegalStateException("Ollama 返回了空响应");
        return parser.parse(response.path("message").path("content").asText(), "Ollama");
    }

    @Override
    public String providerName() {
        return "ollama:" + properties.ai().ollama().chatModel();
    }
}
