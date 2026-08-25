package com.example.aibi.query;

import com.example.aibi.auth.CurrentUserProvider;
import com.example.aibi.common.DatabaseRows;
import com.example.aibi.domain.DomainAccessService;
import com.example.aibi.knowledge.KnowledgeChunk;
import com.example.aibi.knowledge.VectorStorePort;
import com.example.aibi.semantic.SemanticService;
import com.example.aibi.training.TrainingService;
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
    private final DomainAccessService domainAccess;
    private final TrainingService training;

    public QueryOrchestrator(VectorStorePort vectorStore, LlmProvider llm, SemanticService semantic,
                             SqlGuard guard, SafeQueryExecutor executor, JdbcClient jdbc, ObjectMapper mapper,
                             CurrentUserProvider currentUser, VisualizationAdvisor visualizationAdvisor,
                             DomainAccessService domainAccess, TrainingService training) {
        this.vectorStore = vectorStore;
        this.llm = llm;
        this.semantic = semantic;
        this.guard = guard;
        this.executor = executor;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.visualizationAdvisor = visualizationAdvisor;
        this.domainAccess = domainAccess;
        this.training = training;
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public QueryAnswer ask(AskRequest request) {
        return execute(request, Purpose.QUERY);
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public QueryAnswer askForReport(AskRequest request) { return execute(request, Purpose.REPORT); }

    @Transactional(noRollbackFor = RuntimeException.class)
    public QueryAnswer askForTraining(AskRequest request) { return execute(request, Purpose.TRAINING); }

    private QueryAnswer execute(AskRequest request, Purpose purpose) {
        long started = System.nanoTime();
        String runId = UUID.randomUUID().toString();
        String domain = authorize(request.domainOrDefault(), purpose);
        jdbc.sql("INSERT INTO query_run(id,question,status,domain,created_by) VALUES(?,?,'UNDERSTANDING',?,?)")
                .params(runId, request.question(),domain,currentUser.username()).update();
        try {
            List<KnowledgeChunk> context = vectorStore.search(domain, request.question(), 12, Map.of("domain",domain));
            GeneratedQuery generated = llm.generateSql(domain, request.question(), context, semantic.metricsForQuery(domain), semantic.relationsForQuery(domain));
            SqlGuard.ValidationResult validation = guard.validate(generated.sql(), training.authorizedTables(domain));
            List<Map<String, Object>> rows = executor.execute(domain, validation);
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
        String domain=queryDomain(runId); authorize(domain,Purpose.QUERY);
        jdbc.sql("INSERT INTO query_feedback(query_run_id,rating,feedback_comment,corrected_sql) VALUES(?,?,?,?)")
                .params(runId, rating, comment, correctedSql).update();
        return Map.of("queryRunId", runId, "accepted", true);
    }

    public List<Map<String, Object>> history(String rawDomain) {
        String domain=authorize(rawDomain,Purpose.QUERY);
        return DatabaseRows.normalize(jdbc.sql("SELECT id,domain,question,status,generated_sql,answer,error_message,elapsed_ms,created_at FROM query_run WHERE domain=? ORDER BY created_at DESC")
                .param(domain).query().listOfRows());
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

    private String authorize(String domain,Purpose purpose) {
        if(domainAccess==null) return domain;
        return switch(purpose){ case QUERY->domainAccess.requireQuery(domain); case REPORT->domainAccess.requireReport(domain); case TRAINING->domainAccess.requireTrain(domain); };
    }

    private String queryDomain(String runId) {
        return jdbc.sql("SELECT domain FROM query_run WHERE id=?").param(runId).query(String.class).optional()
                .orElseThrow(()->new com.example.aibi.common.BusinessException("QUERY_RUN_NOT_FOUND","查询记录不存在",org.springframework.http.HttpStatus.NOT_FOUND));
    }

    private enum Purpose { QUERY, REPORT, TRAINING }

}
