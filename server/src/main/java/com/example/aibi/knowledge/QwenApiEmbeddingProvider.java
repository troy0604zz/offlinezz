package com.example.aibi.knowledge;

import com.example.aibi.config.AiBiProperties;
import com.example.aibi.config.ModelRuntimeService;
import com.example.aibi.query.QwenApiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "real")
public class QwenApiEmbeddingProvider implements EmbeddingProvider {
    private final AiBiProperties properties;
    private final ModelRuntimeService runtime;
    private final QwenApiClient client;

    public QwenApiEmbeddingProvider(AiBiProperties properties, ModelRuntimeService runtime, QwenApiClient client) {
        this.properties = properties;
        this.runtime = runtime;
        this.client = client;
    }

    @Override
    public List<List<Double>> embeddings(List<String> input) {
        return client.embeddings(modelName(), vectorSize(), input);
    }

    @Override public String providerName() { return ModelRuntimeService.QWEN_API; }
    @Override public String modelName() { return runtime.selection().qwenEmbeddingModel(); }
    @Override public int vectorSize() { return properties.ai().qwenApi().embeddingDimensions(); }
}
