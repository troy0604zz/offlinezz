package com.example.aibi.query;

import com.example.aibi.config.ModelRuntimeService;
import com.example.aibi.knowledge.KnowledgeChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Primary
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "real")
public class RoutingLlmProvider implements LlmProvider {
    private final ModelRuntimeService runtime;
    private final OllamaLlmProvider ollama;
    private final QwenApiLlmProvider qwenApi;

    public RoutingLlmProvider(ModelRuntimeService runtime, OllamaLlmProvider ollama, QwenApiLlmProvider qwenApi) {
        this.runtime = runtime;
        this.ollama = ollama;
        this.qwenApi = qwenApi;
    }

    @Override
    public GeneratedQuery generateSql(String knowledgeDomain, String question, List<KnowledgeChunk> context,
                                      List<Map<String, Object>> metrics, List<Map<String, Object>> relations) {
        return delegate().generateSql(knowledgeDomain, question, context, metrics, relations);
    }

    @Override
    public String providerName() {
        return delegate().providerName();
    }

    @Override
    public String completeJson(String system,String user) { return delegate().completeJson(system,user); }

    private LlmProvider delegate() {
        return ModelRuntimeService.QWEN_API.equals(runtime.selection().chatProvider()) ? qwenApi : ollama;
    }
}
