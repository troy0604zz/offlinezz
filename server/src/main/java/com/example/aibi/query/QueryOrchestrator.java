package com.example.aibi.query;

import com.example.aibi.auth.CurrentUserProvider;
import com.example.aibi.common.DatabaseRows;
import com.example.aibi.knowledge.KnowledgeChunk;
import com.example.aibi.knowledge.VectorStorePort;
import com.example.aibi.semantic.SemanticService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QueryOrchestrator {
    private final VectorStorePort vectorStore;
    private final LlmProvider llm;
    private final SemanticService semantic;
    private final SqlGuard guard;
    private final SafeQueryExecutor executor;
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final CurrentUserProvider currentUser;
    private final VisualizationAdvisor visualizationAdvisor;

    public QueryOrchestrator(VectorStorePort vectorStore, LlmProvider llm, SemanticService semantic,
                             SqlGuard guard, SafeQueryExecutor executor, JdbcClient jdbc, ObjectMapper mapper,
                             CurrentUserProvider currentUser, VisualizationAdvisor visualizationAdvisor) {
        this.vectorStore = vectorStore;
        this.llm = llm;
        this.semantic = semantic;
        this.guard = guard;
        this.executor = executor;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.visualizationAdvisor = visualizationAdvisor;
    }

    @Transactional
    public QueryAnswer ask(AskRequest request) {
        long started = System.nanoTime();
        String runId = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO query_run(id,question,status) VALUES(?,?,'UNDERSTANDING')")
                .params(runId, request.question()).update();
        try {
            List<KnowledgeChunk> context = vectorStore.search(request.domainOrDefault(), request.question(), 6, Map.of());
            GeneratedQuery generated = llm.generateSql(request.domainOrDefault(), request.question(), context, semantic.metrics(), semantic.relations());
            SqlGuard.ValidationResult validation = guard.validate(generated.sql());
            List<Map<String, Object>> rows = executor.execute(validation);
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            String answer = summarize(rows);
            QueryAnswer.ChartSpec chart = visualizationAdvisor.advise(rows);
            jdbc.sql("UPDATE query_run SET generated_sql=?,status='COMPLETED',answer=?,result_json=?,elapsed_ms=? WHERE id=?")
                    .params(validation.sql(), answer, json(rows), elapsed, runId).update();
            audit(runId, validation.tables(), rows.size());
            return new QueryAnswer(runId, request.question(), "COMPLETED", validation.sql(), generated.explanation(),
                    generated.assumptions(), generated.confidence(), validation.tables(), rows, chart, answer,
                    llm.providerName(), vectorStore.providerName(), elapsed);
        } catch (RuntimeException ex) {
            jdbc.sql("UPDATE query_run SET status='FAILED',error_message=? WHERE id=?")
                    .params(ex.getMessage(), runId).update();
            throw ex;
        }
    }

    public Map<String, Object> feedback(String runId, int rating, String comment, String correctedSql) {
        jdbc.sql("INSERT INTO query_feedback(query_run_id,rating,feedback_comment,corrected_sql) VALUES(?,?,?,?)")
                .params(runId, rating, comment, correctedSql).update();
        return Map.of("queryRunId", runId, "accepted", true);
    }

    public List<Map<String, Object>> history() {
        return DatabaseRows.normalize(jdbc.sql("SELECT id,question,status,generated_sql,answer,error_message,elapsed_ms,created_at FROM query_run ORDER BY created_at DESC")
                .query().listOfRows());
    }

    private void audit(String runId, Object tables, int rows) {
        try {
            jdbc.sql("INSERT INTO audit_event(trace_id,event_type,actor,resource_type,resource_id,detail) VALUES(?,'QUERY_EXECUTED',?,'QUERY_RUN',?,?)")
                    .params(runId, currentUser.username(), runId,
                            mapper.writeValueAsString(Map.of("tables", tables, "rowCount", rows))).update();
        } catch (Exception ignored) {
            // Audit serialization must not hide an otherwise successful read-only query in the MVP.
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("查询结果序列化失败", ex);
        }
    }

    private String summarize(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "查询完成，但没有符合条件的数据。";
        if (rows.size() == 1 && rows.get(0).size() == 1) {
            Object value = rows.get(0).values().iterator().next();
            return "查询结果为 " + format(value) + "。";
        }
        return "查询完成，共返回 " + rows.size() + " 行。最高/首条结果：" + rows.get(0) + "。";
    }

    private String format(Object value) {
        if (value instanceof BigDecimal number) return number.stripTrailingZeros().toPlainString();
        return String.valueOf(value);
    }

}
