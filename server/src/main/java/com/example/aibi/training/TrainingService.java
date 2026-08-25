package com.example.aibi.training;

import com.example.aibi.common.BusinessException;
import com.example.aibi.common.DatabaseRows;
import com.example.aibi.domain.DomainAccessService;
import com.example.aibi.knowledge.KnowledgeChunk;
import com.example.aibi.knowledge.KnowledgeService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TrainingService {
    private static final Pattern CREATE_TABLE = Pattern.compile("(?i)\\bCREATE\\s+TABLE\\s+(?:[a-zA-Z0-9_$]+\\.)?[\\\"`]?([a-zA-Z_][a-zA-Z0-9_$]*)");
    private static final Set<String> SALES_TABLES = Set.of(
            "region", "customer", "product", "sales_order", "sales_order_item", "refund",
            "f360_geo", "f360_customer", "f360_application", "f360_technology_node", "f360_fab",
            "f360_process_route", "f360_product", "f360_sales_order", "f360_order_line", "f360_wafer_lot",
            "f360_wafer_output", "f360_yield_result", "f360_shipment", "f360_customer_forecast",
            "f360_capacity_plan", "f360_price_agreement", "f360_product_cost", "f360_inventory_snapshot",
            "f360_quality_incident", "f360_customer_interaction", "f360_design_win", "f360_npi_milestone",
            "f360_equipment_downtime", "f360_customer_score_snapshot", "f360_application_market");

    private final JdbcClient jdbc;
    private final DomainAccessService access;
    private final KnowledgeService knowledge;

    public TrainingService(JdbcClient jdbc, DomainAccessService access, KnowledgeService knowledge) {
        this.jdbc = jdbc;
        this.access = access;
        this.knowledge = knowledge;
    }

    public Map<String, Object> dashboard(String rawDomain) {
        String domain = access.requireTrain(rawDomain);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documents", count("knowledge_document", domain));
        result.put("schemas", count("schema_asset", domain));
        result.put("metrics", count("semantic_metric", domain));
        result.put("relations", count("semantic_relation", domain));
        result.put("synonyms", count("semantic_synonym", domain));
        result.put("sqlExamples", count("sql_example", domain));
        result.put("goldenQuestions", count("golden_question", domain));
        result.put("feedback", jdbc.sql("SELECT COUNT(*) FROM query_feedback f JOIN query_run q ON q.id=f.query_run_id WHERE q.domain=?")
                .param(domain).query(Long.class).single());
        result.put("lastEvaluation", String.valueOf(DatabaseRows.normalize(jdbc.sql(
                "SELECT MAX(last_run_at) AS last_run_at FROM golden_question WHERE domain=?")
                .param(domain).query().singleRow()).get("last_run_at")));
        return result;
    }

    public List<Map<String, Object>> schemas(String domain) {
        return list("SELECT id,domain,name,dialect,ddl_text,description,status,version,created_at FROM schema_asset WHERE domain=? ORDER BY id DESC", access.requireTrain(domain));
    }

    @Transactional
    public Map<String, Object> createSchema(TrainingRequests.SchemaAsset request) {
        String domain = access.requireTrain(request.domain());
        jdbc.sql("INSERT INTO schema_asset(domain,name,dialect,ddl_text,description,status) VALUES(?,?,?,?,?,'PUBLISHED')")
                .params(domain, request.name(), request.dialect(), request.ddlText(), request.description()).update();
        Long id = maxId("schema_asset", domain);
        knowledge.rebuildDomain(domain);
        return Map.of("id", id, "status", "PUBLISHED");
    }

    @Transactional
    public Map<String, Object> updateSchema(long id, TrainingRequests.SchemaAsset request) {
        String domain = owned("schema_asset", id, request.domain());
        jdbc.sql("UPDATE schema_asset SET name=?,dialect=?,ddl_text=?,description=?,version=version+1 WHERE id=? AND domain=?")
                .params(request.name(), request.dialect(), request.ddlText(), request.description(), id, domain).update();
        knowledge.rebuildDomain(domain);
        return updated(id);
    }

    @Transactional
    public Map<String, Object> deleteSchema(long id, String domain) {
        domain=owned("schema_asset",id,domain);
        jdbc.sql("DELETE FROM schema_asset WHERE id=? AND domain=?").params(id,domain).update();
        knowledge.rebuildDomain(domain);
        return deleted(id);
    }

    public List<Map<String, Object>> synonyms(String domain) {
        return list("SELECT * FROM semantic_synonym WHERE domain=? ORDER BY id DESC", access.requireTrain(domain));
    }

    @Transactional
    public Map<String, Object> createSynonym(TrainingRequests.Synonym request) {
        String domain = access.requireTrain(request.domain());
        jdbc.sql("INSERT INTO semantic_synonym(domain,business_term,synonyms,target_expression,status) VALUES(?,?,?,?,'PUBLISHED')")
                .params(domain, request.businessTerm(), request.synonyms(), request.targetExpression()).update();
        return Map.of("id", maxId("semantic_synonym", domain), "created", true);
    }

    @Transactional
    public Map<String, Object> updateSynonym(long id, TrainingRequests.Synonym request) {
        String domain = owned("semantic_synonym", id, request.domain());
        jdbc.sql("UPDATE semantic_synonym SET business_term=?,synonyms=?,target_expression=? WHERE id=? AND domain=?")
                .params(request.businessTerm(), request.synonyms(), request.targetExpression(), id, domain).update();
        return updated(id);
    }

    @Transactional
    public Map<String, Object> deleteSynonym(long id, String domain) {
        return deleteRow("semantic_synonym", id, domain);
    }

    public List<Map<String, Object>> examples(String domain) {
        return list("SELECT id,domain,question,sql_text,explanation,status,version,hit_count,created_at FROM sql_example WHERE domain=? ORDER BY id DESC", access.requireTrain(domain));
    }

    @Transactional
    public Map<String, Object> createExample(TrainingRequests.SqlExample request) {
        String domain = access.requireTrain(request.domain());
        jdbc.sql("INSERT INTO sql_example(domain,question,sql_text,explanation,status) VALUES(?,?,?,?,'DRAFT')")
                .params(domain, request.question(), request.sql(), request.explanation()).update();
        return Map.of("id", maxId("sql_example", domain), "created", true, "status", "DRAFT");
    }

    @Transactional
    public Map<String, Object> updateExample(long id, TrainingRequests.SqlExample request) {
        String domain = owned("sql_example", id, request.domain());
        jdbc.sql("UPDATE sql_example SET question=?,sql_text=?,explanation=?,status='DRAFT',version=version+1 WHERE id=? AND domain=?")
                .params(request.question(), request.sql(), request.explanation(), id, domain).update();
        knowledge.rebuildDomain(domain);
        return updated(id);
    }

    @Transactional
    public Map<String, Object> deleteExample(long id, String domain) {
        domain=owned("sql_example",id,domain);
        jdbc.sql("DELETE FROM sql_example WHERE id=? AND domain=?").params(id,domain).update();
        knowledge.rebuildDomain(domain);
        return deleted(id);
    }

    @Transactional
    public Map<String, Object> publishExample(long id, String domain) {
        domain = owned("sql_example", id, domain);
        int changed = jdbc.sql("UPDATE sql_example SET status='PUBLISHED',version=version+1 WHERE id=? AND domain=?")
                .params(id, domain).update();
        knowledge.rebuildDomain(domain);
        return Map.of("id", id, "published", changed == 1);
    }

    public List<SqlExampleMatch> relevantExamples(String domain, String question, int limit) {
        return relevantExamples(domain,question,List.of(),limit);
    }

    public List<SqlExampleMatch> relevantExamples(String domain,String question,List<KnowledgeChunk> context,int limit) {
        String expanded = expandSynonyms(domain, question);
        List<SqlExampleMatch> lexical=jdbc.sql("SELECT id,question,sql_text,explanation FROM sql_example WHERE domain=? AND status='PUBLISHED'")
                .param(domain).query((rs, row) -> new SqlExampleMatch(rs.getLong("id"), rs.getString("question"),
                        rs.getString("sql_text"), rs.getString("explanation"),
                        similarity(expanded, expandSynonyms(domain, rs.getString("question")))))
                .list();
        Map<Long,Double> semanticScores=new HashMap<>();
        for(KnowledgeChunk chunk:context) {
            if(!"SQL_EXAMPLE".equals(String.valueOf(chunk.metadata().get("assetType")))) continue;
            try {
                long id=Long.parseLong(String.valueOf(chunk.metadata().get("assetId")));
                double score=Math.max(0,Math.min(1,chunk.score()));
                // Semantic retrieval participates in ranking, but only an exceptionally strong match can
                // cross the direct-execution threshold used by SqlPromptFactory.
                semanticScores.merge(id,score>=0.92?score:score*0.80,Math::max);
            } catch(NumberFormatException ignored) { }
        }
        return lexical.stream().map(item->new SqlExampleMatch(item.id(),item.question(),item.sql(),item.explanation(),
                        Math.max(item.score(),semanticScores.getOrDefault(item.id(),0D))))
                .sorted(Comparator.comparingDouble(SqlExampleMatch::score).reversed()).limit(limit).toList();
    }

    public String expandSynonyms(String domain, String input) {
        String result = normalize(input);
        for (Map<String, Object> row : list("SELECT business_term,synonyms FROM semantic_synonym WHERE domain=? AND status='PUBLISHED'", domain)) {
            String term = String.valueOf(row.get("business_term"));
            for (String synonym : String.valueOf(row.get("synonyms")).split(",")) {
                if (!synonym.isBlank() && result.contains(normalize(synonym))) result += normalize(term);
            }
        }
        return result;
    }

    public List<Map<String, Object>> goldenQuestions(String domain) {
        return list("SELECT * FROM golden_question WHERE domain=? ORDER BY id DESC", access.requireTrain(domain));
    }

    @Transactional
    public Map<String, Object> createGolden(TrainingRequests.GoldenQuestion request) {
        String domain = access.requireTrain(request.domain());
        jdbc.sql("INSERT INTO golden_question(domain,question,expected_sql,expected_result_json,status) VALUES(?,?,?,?,'ENABLED')")
                .params(domain, request.question(), request.expectedSql(), request.expectedResultJson()).update();
        return Map.of("id", maxId("golden_question", domain), "created", true);
    }

    @Transactional
    public Map<String, Object> updateGolden(long id, TrainingRequests.GoldenQuestion request) {
        String domain = owned("golden_question", id, request.domain());
        jdbc.sql("UPDATE golden_question SET question=?,expected_sql=?,expected_result_json=?,last_run_status=NULL,last_score=NULL,last_detail=NULL,last_run_at=NULL WHERE id=? AND domain=?")
                .params(request.question(), request.expectedSql(), request.expectedResultJson(), id, domain).update();
        return updated(id);
    }

    @Transactional
    public Map<String, Object> deleteGolden(long id, String domain) {
        return deleteRow("golden_question", id, domain);
    }

    public Map<String, Object> golden(long id, String domain) {
        domain = owned("golden_question", id, domain);
        return DatabaseRows.normalize(jdbc.sql("SELECT * FROM golden_question WHERE id=? AND domain=?")
                .params(id, domain).query().singleRow());
    }

    @Transactional
    public void saveEvaluation(long id, String domain, String status, double score, String detail) {
        jdbc.sql("UPDATE golden_question SET last_run_status=?,last_score=?,last_detail=?,last_run_at=CURRENT_TIMESTAMP WHERE id=? AND domain=?")
                .params(status, score, detail, id, domain).update();
    }

    public List<Map<String, Object>> feedback(String domain) {
        domain = access.requireTrain(domain);
        return list("SELECT f.id,f.query_run_id,q.question,q.generated_sql,f.rating,f.feedback_comment AS \"comment\",f.corrected_sql,f.created_at FROM query_feedback f JOIN query_run q ON q.id=f.query_run_id WHERE q.domain=? ORDER BY f.id DESC", domain);
    }

    @Transactional
    public Map<String, Object> promoteFeedback(long id, String rawDomain) {
        String domain = access.requireTrain(rawDomain);
        List<Map<String, Object>> rows = jdbc.sql("SELECT q.question,q.generated_sql,f.corrected_sql FROM query_feedback f JOIN query_run q ON q.id=f.query_run_id WHERE f.id=? AND q.domain=?")
                .params(id, domain).query().listOfRows();
        if (rows.isEmpty()) throw notFound();
        Map<String, Object> row = DatabaseRows.normalize(rows.get(0));
        String corrected = row.get("corrected_sql") == null ? "" : String.valueOf(row.get("corrected_sql"));
        String sql = corrected.isBlank() ? String.valueOf(row.get("generated_sql")) : corrected;
        jdbc.sql("INSERT INTO sql_example(domain,question,sql_text,explanation,status) VALUES(?,?,?,?,'DRAFT')")
                .params(domain, String.valueOf(row.get("question")), sql, "由用户反馈 #" + id + " 转换").update();
        return Map.of("promoted", true, "status", "DRAFT");
    }

    public Set<String> authorizedTables(String rawDomain) {
        String domain = access.normalize(rawDomain);
        Set<String> tables = new LinkedHashSet<>();
        for (String ddl : jdbc.sql("SELECT ddl_text FROM schema_asset WHERE domain=? AND status='PUBLISHED'")
                .param(domain).query(String.class).list()) {
            Matcher matcher = CREATE_TABLE.matcher(ddl);
            while (matcher.find()) tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        if ("sales".equals(domain)) tables.addAll(SALES_TABLES);
        return tables;
    }

    private List<Map<String, Object>> list(String sql, String domain) {
        return DatabaseRows.normalize(jdbc.sql(sql).param(domain).query().listOfRows());
    }
    private long count(String table, String domain) { return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE domain=?").param(domain).query(Long.class).single(); }
    private Long maxId(String table, String domain) { return jdbc.sql("SELECT MAX(id) FROM " + table + " WHERE domain=?").param(domain).query(Long.class).single(); }
    private String owned(String table, long id, String rawDomain) {
        String domain = access.requireTrain(rawDomain);
        if (jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE id=? AND domain=?").params(id, domain).query(Integer.class).single() == 0) throw notFound();
        return domain;
    }
    private Map<String, Object> deleteRow(String table, long id, String domain) {
        domain = owned(table, id, domain);
        jdbc.sql("DELETE FROM " + table + " WHERE id=? AND domain=?").params(id, domain).update();
        return deleted(id);
    }
    private Map<String, Object> updated(long id) { return Map.of("id", id, "updated", true); }
    private Map<String, Object> deleted(long id) { return Map.of("id", id, "deleted", true); }
    private BusinessException notFound() { return new BusinessException("RESOURCE_NOT_FOUND", "域内训练资产不存在", HttpStatus.NOT_FOUND); }
    private String normalize(String text) { return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", ""); }
    private double similarity(String a, String b) {
        if (a.equals(b)) return 1;
        Set<String> aa = grams(a), bb = grams(b);
        if (aa.isEmpty() || bb.isEmpty()) return 0;
        Set<String> both = new HashSet<>(aa); both.retainAll(bb);
        Set<String> all = new HashSet<>(aa); all.addAll(bb);
        return (double) both.size() / all.size();
    }
    private Set<String> grams(String value) {
        Set<String> result = new HashSet<>();
        for (int index = 0; index < Math.max(0, value.length() - 1); index++) result.add(value.substring(index, index + 2));
        return result;
    }
}
