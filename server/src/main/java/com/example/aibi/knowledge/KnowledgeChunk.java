package com.example.aibi.knowledge;

import java.util.Map;

public record KnowledgeChunk(String id, String content, Map<String, Object> metadata, double score) {}

