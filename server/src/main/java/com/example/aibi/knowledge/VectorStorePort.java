package com.example.aibi.knowledge;

import java.util.List;
import java.util.Map;

public interface VectorStorePort {
    void upsert(String knowledgeDomain, List<KnowledgeChunk> chunks);
    List<KnowledgeChunk> search(String knowledgeDomain, String query, int topK, Map<String, Object> filters);
    String providerName();
}

