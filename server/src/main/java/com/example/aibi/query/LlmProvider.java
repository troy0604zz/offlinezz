package com.example.aibi.query;

import com.example.aibi.knowledge.KnowledgeChunk;

import java.util.List;
import java.util.Map;

public interface LlmProvider {
    GeneratedQuery generateSql(String knowledgeDomain, String question, List<KnowledgeChunk> context, List<Map<String, Object>> metrics,
                               List<Map<String, Object>> relations);
    String completeJson(String system, String user);
    String providerName();
}
