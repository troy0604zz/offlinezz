package com.example.aibi.knowledge;

import java.util.List;

public interface EmbeddingProvider {
    List<List<Double>> embeddings(List<String> input);
    String providerName();
    String modelName();
    int vectorSize();
}
