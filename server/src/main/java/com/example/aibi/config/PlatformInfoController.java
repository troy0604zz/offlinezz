package com.example.aibi.config;

import com.example.aibi.knowledge.VectorStorePort;
import com.example.aibi.query.LlmProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformInfoController {
    private final AiBiProperties properties;
    private final ModelRuntimeService runtime;
    private final LlmProvider llm;
    private final VectorStorePort vectorStore;

    public PlatformInfoController(AiBiProperties properties, ModelRuntimeService runtime,
                                  LlmProvider llm, VectorStorePort vectorStore) {
        this.properties = properties;
        this.runtime = runtime;
        this.llm = llm;
        this.vectorStore = vectorStore;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of("aiMode", properties.ai().mode(), "llmProvider", llm.providerName(),
                "vectorProvider", vectorStore.providerName(),
                "chatProvider", runtime.selection().chatProvider(),
                "embeddingProvider", runtime.selection().embeddingProvider(),
                "chatModel", runtime.activeChatModel(),
                "embeddingModel", runtime.activeEmbeddingModel(), "vectorSize", runtime.vectorSize());
    }
}
