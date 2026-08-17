package com.example.aibi.knowledge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "mock", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStorePort {
    private final Map<String, List<KnowledgeChunk>> data = new ConcurrentHashMap<>();

    @Override
    public void upsert(String knowledgeDomain, List<KnowledgeChunk> chunks) {
        List<KnowledgeChunk> target=data.computeIfAbsent(knowledgeDomain, ignored -> new ArrayList<>());
        synchronized (target) {
            var ids=chunks.stream().map(KnowledgeChunk::id).collect(java.util.stream.Collectors.toSet());
            target.removeIf(existing->ids.contains(existing.id()));
            target.addAll(chunks);
        }
    }

    @Override
    public List<KnowledgeChunk> search(String knowledgeDomain, String query, int topK, Map<String, Object> filters) {
        List<String> tokens = tokenize(query);
        return data.getOrDefault(knowledgeDomain, List.of()).stream()
                .filter(chunk -> filters.entrySet().stream().allMatch(e -> e.getValue().equals(chunk.metadata().get(e.getKey()))))
                .map(chunk -> new KnowledgeChunk(chunk.id(), chunk.content(), chunk.metadata(), score(tokens, chunk.content())))
                .sorted(Comparator.comparingDouble(KnowledgeChunk::score).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public String providerName() { return "in-memory-mock"; }

    private double score(List<String> tokens, String content) {
        if (tokens.isEmpty()) return 0;
        long matched = tokens.stream().filter(content.toLowerCase()::contains).count();
        return (double) matched / tokens.size();
    }

    private List<String> tokenize(String text) {
        return text.toLowerCase().replaceAll("[^\\p{L}\\p{N}_]+", " ").lines()
                .flatMap(line -> List.of(line.split("\\s+")).stream())
                .filter(token -> !token.isBlank()).toList();
    }
}
