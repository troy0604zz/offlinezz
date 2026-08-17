package com.example.aibi.config;

import com.example.aibi.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;

@Service
public class ModelRuntimeService {
    public static final String OLLAMA = "ollama";
    public static final String QWEN_API = "qwen-api";

    private final AiBiProperties properties;
    private volatile RuntimeSelection selection;

    public ModelRuntimeService(AiBiProperties properties) {
        this.properties = properties;
        this.selection = new RuntimeSelection(
                provider(properties.ai().chatProvider()),
                provider(properties.ai().embeddingProvider()),
                properties.ai().qwenApi().chatModel(),
                properties.ai().qwenApi().embeddingModel());
    }

    public RuntimeSelection selection() {
        return selection;
    }

    public synchronized UpdateResult update(UpdateRequest request) {
        RuntimeSelection before = selection;
        String chatProvider = provider(request.chatProvider());
        String embeddingProvider = provider(request.embeddingProvider());
        String chatModel = required(request.qwenChatModel(), "千问聊天模型不能为空");
        String embeddingModel = required(request.qwenEmbeddingModel(), "千问向量模型不能为空");
        if (QWEN_API.equals(chatProvider) || QWEN_API.equals(embeddingProvider)) {
            requireQwenApiKey();
        }
        selection = new RuntimeSelection(chatProvider, embeddingProvider, chatModel, embeddingModel);
        boolean embeddingChanged = !before.embeddingProvider().equals(embeddingProvider)
                || (QWEN_API.equals(embeddingProvider) && !before.qwenEmbeddingModel().equals(embeddingModel));
        return new UpdateResult(snapshot(), embeddingChanged);
    }

    public RuntimeSnapshot snapshot() {
        RuntimeSelection current = selection;
        return new RuntimeSnapshot(
                properties.ai().mode(),
                current.chatProvider(),
                current.embeddingProvider(),
                activeChatModel(),
                activeEmbeddingModel(),
                properties.ai().ollama().chatModel(),
                properties.ai().ollama().embeddingModel(),
                current.qwenChatModel(),
                current.qwenEmbeddingModel(),
                qwenConfigured(),
                safeApiHost(),
                vectorSize());
    }

    public String activeChatModel() {
        return QWEN_API.equals(selection.chatProvider())
                ? selection.qwenChatModel() : properties.ai().ollama().chatModel();
    }

    public String activeEmbeddingModel() {
        return QWEN_API.equals(selection.embeddingProvider())
                ? selection.qwenEmbeddingModel() : properties.ai().ollama().embeddingModel();
    }

    public int vectorSize() {
        return QWEN_API.equals(selection.embeddingProvider())
                ? properties.ai().qwenApi().embeddingDimensions() : properties.ai().qdrant().vectorSize();
    }

    public boolean qwenConfigured() {
        String key = properties.ai().qwenApi().apiKey();
        return key != null && !key.isBlank();
    }

    public void requireQwenApiKey() {
        if (!qwenConfigured()) {
            throw new BusinessException("QWEN_API_KEY_MISSING",
                    "服务器尚未配置 DASHSCOPE_API_KEY，不能切换到千问官方 API",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String safeApiHost() {
        try {
            URI uri = URI.create(properties.ai().qwenApi().baseUrl());
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (RuntimeException ex) {
            return "未配置";
        }
    }

    private String provider(String value) {
        String normalized = required(value, "模型提供方不能为空").toLowerCase(Locale.ROOT);
        if (!OLLAMA.equals(normalized) && !QWEN_API.equals(normalized)) {
            throw new BusinessException("UNSUPPORTED_AI_PROVIDER", "不支持的模型提供方：" + value,
                    HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("INVALID_MODEL_RUNTIME", message, HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    public record RuntimeSelection(String chatProvider, String embeddingProvider,
                                   String qwenChatModel, String qwenEmbeddingModel) {}

    public record RuntimeSnapshot(String mode, String activeChatProvider, String activeEmbeddingProvider,
                                  String activeChatModel, String activeEmbeddingModel,
                                  String ollamaChatModel, String ollamaEmbeddingModel,
                                  String qwenChatModel, String qwenEmbeddingModel,
                                  boolean qwenApiConfigured, String qwenApiHost, int vectorSize) {}

    public record UpdateRequest(String chatProvider, String embeddingProvider,
                                String qwenChatModel, String qwenEmbeddingModel,
                                boolean reindexKnowledge) {}

    public record UpdateResult(RuntimeSnapshot runtime, boolean embeddingChanged) {}
}
