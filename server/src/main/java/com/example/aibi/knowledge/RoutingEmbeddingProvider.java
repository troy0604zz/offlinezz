package com.example.aibi.knowledge;

import com.example.aibi.config.ModelRuntimeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Primary
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "real")
public class RoutingEmbeddingProvider implements EmbeddingProvider {
    private final ModelRuntimeService runtime;
    private final OllamaEmbeddingProvider ollama;
    private final QwenApiEmbeddingProvider qwenApi;

    public RoutingEmbeddingProvider(ModelRuntimeService runtime, OllamaEmbeddingProvider ollama,
                                    QwenApiEmbeddingProvider qwenApi) {
        this.runtime = runtime;
        this.ollama = ollama;
        this.qwenApi = qwenApi;
    }

    @Override public List<List<Double>> embeddings(List<String> input) { return delegate().embeddings(input); }
    @Override public String providerName() { return delegate().providerName(); }
    @Override public String modelName() { return delegate().modelName(); }
    @Override public int vectorSize() { return delegate().vectorSize(); }

    private EmbeddingProvider delegate() {
        return ModelRuntimeService.QWEN_API.equals(runtime.selection().embeddingProvider()) ? qwenApi : ollama;
    }
}
