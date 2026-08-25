package com.example.aibi.knowledge;

import com.example.aibi.config.AiBiProperties;
import com.example.aibi.config.ModelRuntimeService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "real")
public class QdrantVectorStoreAdapter implements VectorStorePort {
    private final AiBiProperties properties;
    private final RestClient qdrant;
    private final EmbeddingProvider embeddings;

    public QdrantVectorStoreAdapter(AiBiProperties properties, RestClient.Builder builder,
                                    EmbeddingProvider embeddings) {
        this.properties = properties;
        this.embeddings = embeddings;
        RestClient.Builder qdrantBuilder = builder.clone().baseUrl(properties.ai().qdrant().baseUrl());
        if (properties.ai().qdrant().apiKey() != null && !properties.ai().qdrant().apiKey().isBlank()) {
            qdrantBuilder.defaultHeader("api-key", properties.ai().qdrant().apiKey());
        }
        this.qdrant = qdrantBuilder.build();
    }

    @Override
    public void upsert(String knowledgeDomain, List<KnowledgeChunk> chunks) {
        String collection = collection(knowledgeDomain);
        ensureCollection(collection);
        List<List<Double>> vectors = embeddings.embeddings(chunks.stream().map(KnowledgeChunk::content).toList());
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            Map<String, Object> payload = new LinkedHashMap<>(chunk.metadata());
            payload.put("content", chunk.content());
            payload.put("embeddingProvider", embeddings.providerName());
            payload.put("embeddingModel", embeddings.modelName());
            points.add(Map.of("id", stableUuid(chunk.id()), "vector", vectors.get(i), "payload", payload));
        }
        qdrant.put().uri("/collections/{name}/points?wait=true", collection)
                .body(Map.of("points", points)).retrieve().toBodilessEntity();
    }

    @Override
    public void clear(String knowledgeDomain) {
        deleteCollection(collection(knowledgeDomain));
    }

    @Override
    public List<String> purgeManaged(String knowledgeDomain, VectorAssetType assetType) {
        List<String> collections = managedCollections();
        if (knowledgeDomain == null && assetType == null) {
            collections.forEach(this::deleteCollection);
            return collections;
        }
        List<Map<String, Object>> must = new ArrayList<>();
        if (knowledgeDomain != null) {
            must.add(Map.of("key", "domain", "match", Map.of("value", knowledgeDomain)));
        }
        if (assetType != null) {
            must.add(Map.of("key", "assetType", "match", Map.of("value", assetType.name())));
        }
        Map<String, Object> request = Map.of("filter", Map.of("must", must));
        for (String collection : collections) {
            try {
                qdrant.post().uri("/collections/{name}/points/delete?wait=true", collection)
                        .body(request).retrieve().toBodilessEntity();
            } catch (RestClientResponseException responseError) {
                if (responseError.getStatusCode().value() != 404) throw responseError;
            }
        }
        return collections;
    }

    @Override
    public void invalidateOtherEmbeddings(String knowledgeDomain) {
        String active=collection(knowledgeDomain);
        String prefix=sanitize(properties.ai().qdrant().collectionPrefix())+"_";
        String suffix="_"+sanitize(knowledgeDomain);
        JsonNode response=qdrant.get().uri("/collections").retrieve().body(JsonNode.class);
        if(response==null) throw new IllegalStateException("Qdrant 集合列表为空");
        for(JsonNode item:response.path("result").path("collections")) {
            String name=item.path("name").asText();
            if(!name.equals(active)&&name.startsWith(prefix)&&name.endsWith(suffix)) deleteCollection(name);
        }
    }

    @Override
    public List<KnowledgeChunk> search(String knowledgeDomain, String query, int topK, Map<String, Object> filters) {
        String collection = collection(knowledgeDomain);
        ensureCollection(collection);
        List<Double> vector = embeddings.embeddings(List.of(query)).get(0);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", vector);
        request.put("limit", topK);
        request.put("with_payload", true);
        if (!filters.isEmpty()) {
            request.put("filter", Map.of("must", filters.entrySet().stream()
                    .map(e -> Map.of("key", e.getKey(), "match", Map.of("value", e.getValue()))).toList()));
        }
        JsonNode response = qdrant.post().uri("/collections/{name}/points/query", collection)
                .body(request).retrieve().body(JsonNode.class);
        if (response == null) return List.of();
        JsonNode points = response.path("result").path("points");
        List<KnowledgeChunk> result = new ArrayList<>();
        for (JsonNode point : points) {
            Map<String, Object> payload = new LinkedHashMap<>();
            point.path("payload").fields().forEachRemaining(e -> payload.put(e.getKey(), scalar(e.getValue())));
            result.add(new KnowledgeChunk(point.path("id").asText(), point.path("payload").path("content").asText(),
                    payload, point.path("score").asDouble()));
        }
        return result;
    }

    @Override public String providerName() { return "qdrant"; }

    private void ensureCollection(String collection) {
        try {
            qdrant.get().uri("/collections/{name}", collection).retrieve().toBodilessEntity();
        } catch (Exception missing) {
            qdrant.put().uri("/collections/{name}", collection)
                    .body(Map.of("vectors", Map.of("size", embeddings.vectorSize(), "distance", "Cosine")))
                    .retrieve().toBodilessEntity();
        }
    }

    private Object scalar(JsonNode node) {
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNumber()) return node.asDouble();
        return node.asText();
    }

    private String collection(String domain) {
        String prefix = properties.ai().qdrant().collectionPrefix();
        String identity = ModelRuntimeService.OLLAMA.equals(embeddings.providerName())
                ? "" : "_" + embeddings.providerName() + "_" + embeddings.modelName();
        return (prefix + identity + "_" + domain).replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String sanitize(String value) { return value.replaceAll("[^a-zA-Z0-9_-]", "_"); }

    private List<String> managedCollections() {
        String prefix = sanitize(properties.ai().qdrant().collectionPrefix()) + "_";
        JsonNode response = qdrant.get().uri("/collections").retrieve().body(JsonNode.class);
        if (response == null) throw new IllegalStateException("Qdrant 集合列表为空");
        List<String> result = new ArrayList<>();
        for (JsonNode item : response.path("result").path("collections")) {
            String name = item.path("name").asText();
            if (name.startsWith(prefix)) result.add(name);
        }
        return result;
    }

    private void deleteCollection(String name) {
        try {
            qdrant.delete().uri("/collections/{name}", name).retrieve().toBodilessEntity();
        } catch (RestClientResponseException responseError) {
            if (responseError.getStatusCode().value() != 404) throw responseError;
            // A missing collection is already the desired state. Connection and server errors must propagate.
        }
    }

    private String stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
