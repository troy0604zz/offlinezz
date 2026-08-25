package com.example.aibi.knowledge;

import com.example.aibi.auth.CurrentUserProvider;
import com.example.aibi.common.BusinessException;
import com.example.aibi.domain.DomainAccessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class VectorDataAdminService {
    public static final String CONFIRMATION = "DELETE_QDRANT_AND_ORACLE";

    private static final Map<VectorAssetType, String> ASSET_TABLES = Map.of(
            VectorAssetType.DOCUMENT, "knowledge_document",
            VectorAssetType.SCHEMA, "schema_asset",
            VectorAssetType.METRIC, "semantic_metric",
            VectorAssetType.SQL_EXAMPLE, "sql_example"
    );

    private final JdbcClient jdbc;
    private final VectorStorePort vectors;
    private final DomainAccessService access;
    private final CurrentUserProvider currentUser;
    private final KnowledgeService knowledge;
    private final ObjectMapper mapper;

    public VectorDataAdminService(JdbcClient jdbc, VectorStorePort vectors, DomainAccessService access,
                                  CurrentUserProvider currentUser, KnowledgeService knowledge, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.vectors = vectors;
        this.access = access;
        this.currentUser = currentUser;
        this.knowledge = knowledge;
        this.mapper = mapper;
    }

    public Map<String, Object> preview(String rawDomain, String rawAssetType) {
        Scope scope = scope(rawDomain, rawAssetType);
        return summary(scope, false, List.of());
    }

    @Transactional
    public Map<String, Object> purge(VectorDataPurgeRequest request) {
        if (!CONFIRMATION.equals(request.confirmation())) {
            throw new BusinessException("PURGE_CONFIRMATION_REQUIRED",
                    "清理会同时永久删除 Oracle 权威数据；confirmation 必须为 " + CONFIRMATION,
                    HttpStatus.BAD_REQUEST);
        }
        Scope scope = scope(request.domain(), request.assetType());
        Map<VectorAssetType, Long> deleted = counts(scope);
        List<Path> sourceFiles = scope.types().contains(VectorAssetType.DOCUMENT)
                ? documentPaths(scope.domains()) : List.of();

        for (String domain : scope.domains()) {
            for (VectorAssetType type : scope.types()) {
                jdbc.sql("DELETE FROM " + ASSET_TABLES.get(type) + " WHERE domain=?").param(domain).update();
            }
        }

        AtomicBoolean vectorMutationStarted = new AtomicBoolean(false);
        registerCompletion(scope, sourceFiles, vectorMutationStarted);
        List<String> scannedCollections;
        try {
            vectorMutationStarted.set(true);
            scannedCollections = vectors.purgeManaged(scope.global() ? null : scope.domains().get(0), scope.singleType());
        } catch (Exception ex) {
            throw new BusinessException("VECTOR_PURGE_FAILED",
                    "Qdrant 清理失败，Oracle 删除已回滚：" + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }

        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("scope", scope.global() ? "ALL_DOMAINS" : scope.domains());
        auditDetail.put("assetTypes", scope.types());
        auditDetail.put("oracleDeleted", deleted);
        auditDetail.put("qdrantCollectionsScanned", scannedCollections);
        jdbc.sql("INSERT INTO audit_event(trace_id,event_type,actor,resource_type,resource_id,detail) " +
                        "VALUES(?,'VECTOR_DATA_PURGED',?,'VECTOR_DATA',?,?)")
                .params(UUID.randomUUID().toString(), currentUser.username(),
                        scope.global() ? "ALL" : scope.domains().get(0), json(auditDetail)).update();

        return summary(scope, true, scannedCollections, deleted, sourceFiles.size());
    }

    private Scope scope(String rawDomain, String rawAssetType) {
        VectorAssetType type = parseType(rawAssetType);
        if (rawDomain != null && !rawDomain.isBlank()) {
            return new Scope(false, List.of(access.requireTrain(rawDomain)),
                    type == null ? List.of(VectorAssetType.values()) : List.of(type));
        }
        requireGlobalAdmin();
        List<String> domains = jdbc.sql("SELECT code FROM data_domain ORDER BY code").query(String.class).list();
        return new Scope(true, domains, type == null ? List.of(VectorAssetType.values()) : List.of(type));
    }

    private VectorAssetType parseType(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) return null;
        try {
            return VectorAssetType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_VECTOR_ASSET_TYPE",
                    "assetType 仅支持 ALL、DOCUMENT、SCHEMA、METRIC、SQL_EXAMPLE", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireGlobalAdmin() {
        Integer count = jdbc.sql("SELECT COUNT(*) FROM app_user_role ur JOIN app_role r ON r.id=ur.role_id " +
                        "WHERE ur.user_id=? AND r.code='AI_ADMIN'")
                .param(currentUser.userId()).query(Integer.class).single();
        if (count == null || count == 0) {
            throw new BusinessException("GLOBAL_PURGE_FORBIDDEN", "跨数据域清理仅允许 AI 管理员执行", HttpStatus.FORBIDDEN);
        }
    }

    private Map<VectorAssetType, Long> counts(Scope scope) {
        Map<VectorAssetType, Long> result = new EnumMap<>(VectorAssetType.class);
        for (VectorAssetType type : scope.types()) {
            long count = 0;
            for (String domain : scope.domains()) {
                count += jdbc.sql("SELECT COUNT(*) FROM " + ASSET_TABLES.get(type) + " WHERE domain=?")
                        .param(domain).query(Long.class).single();
            }
            result.put(type, count);
        }
        return result;
    }

    private List<Path> documentPaths(List<String> domains) {
        List<Path> result = new ArrayList<>();
        for (String domain : domains) {
            for (String path : jdbc.sql("SELECT storage_path FROM knowledge_document WHERE domain=?")
                    .param(domain).query(String.class).list()) {
                try { result.add(Path.of(path)); } catch (Exception ignored) { }
            }
        }
        return result;
    }

    private void registerCompletion(Scope scope, List<Path> sourceFiles, AtomicBoolean vectorMutationStarted) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Path path : sourceFiles) {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK || !vectorMutationStarted.get()) return;
                for (String domain : scope.domains()) {
                    try { knowledge.rebuildDomain(domain); } catch (Exception ignored) { }
                }
            }
        });
    }

    private Map<String, Object> summary(Scope scope, boolean deleted, List<String> collections) {
        return summary(scope, deleted, collections, counts(scope), 0);
    }

    private Map<String, Object> summary(Scope scope, boolean deleted, List<String> collections,
                                        Map<VectorAssetType, Long> oracleCounts, int sourceFiles) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("scope", scope.global() ? "ALL_DOMAINS" : "DOMAIN");
        result.put("domains", scope.domains());
        result.put("assetTypes", scope.types());
        result.put(deleted ? "oracleDeleted" : "oracleMatched", oracleCounts);
        result.put("qdrantProvider", vectors.providerName());
        if (deleted) {
            result.put("qdrantCollectionsScanned", collections);
            result.put("sourceFilesScheduledForDeletion", sourceFiles);
            result.put("preservedOracleTypes", List.of("SEMANTIC_RELATION", "SEMANTIC_SYNONYM", "GOLDEN_QUESTION"));
        } else {
            result.put("requiredConfirmation", CONFIRMATION);
        }
        return result;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { return String.valueOf(value); }
    }

    private record Scope(boolean global, List<String> domains, List<VectorAssetType> types) {
        VectorAssetType singleType() { return types.size() == 1 ? types.get(0) : null; }
    }
}
