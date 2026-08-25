package com.example.aibi.knowledge;

import java.util.List;
import java.util.Map;

public interface VectorStorePort {
    void upsert(String knowledgeDomain, List<KnowledgeChunk> chunks);
    void clear(String knowledgeDomain);
    List<String> purgeManaged(String knowledgeDomain, VectorAssetType assetType);
    void invalidateOtherEmbeddings(String knowledgeDomain);
    List<KnowledgeChunk> search(String knowledgeDomain, String query, int topK, Map<String, Object> filters);
    String providerName();
}
