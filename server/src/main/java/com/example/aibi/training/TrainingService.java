package com.example.aibi.training;

import com.example.aibi.common.DatabaseRows;
import com.example.aibi.knowledge.KnowledgeChunk;
import com.example.aibi.knowledge.VectorStorePort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TrainingService {
    private final JdbcClient jdbc;
    private final VectorStorePort vectorStore;

    public TrainingService(JdbcClient jdbc, VectorStorePort vectorStore) {
        this.jdbc = jdbc;
        this.vectorStore = vectorStore;
    }

    public Map<String,Object> dashboard() {
        return Map.of(
                "documents", count("knowledge_document"), "schemas", count("schema_asset"),
                "metrics", count("semantic_metric"), "relations", count("semantic_relation"),
                "synonyms", count("semantic_synonym"), "sqlExamples", count("sql_example"),
                "goldenQuestions", count("golden_question"), "feedback", count("query_feedback"),
                "lastEvaluation", String.valueOf(DatabaseRows.normalize(jdbc.sql("SELECT MAX(last_run_at) AS last_run_at FROM golden_question").query().singleRow()).get("last_run_at")));
    }

    public List<Map<String,Object>> schemas() { return DatabaseRows.normalize(jdbc.sql("SELECT id,domain,name,dialect,description,status,version,created_at FROM schema_asset ORDER BY id DESC").query().listOfRows()); }
    @Transactional public Map<String,Object> createSchema(TrainingRequests.SchemaAsset r) {
        jdbc.sql("INSERT INTO schema_asset(domain,name,dialect,ddl_text,description,status) VALUES(?,?,?,?,?,'PUBLISHED')")
                .params(r.domain(),r.name(),r.dialect(),r.ddlText(),r.description()).update();
        Long id=jdbc.sql("SELECT MAX(id) FROM schema_asset").query(Long.class).single();
        vectorStore.upsert(r.domain(),List.of(new KnowledgeChunk("schema-"+id,r.ddlText(),Map.of("domain",r.domain(),"type","DDL","name",r.name()),1)));
        return Map.of("id",id,"status","PUBLISHED");
    }

    public List<Map<String,Object>> synonyms() { return DatabaseRows.normalize(jdbc.sql("SELECT * FROM semantic_synonym ORDER BY id DESC").query().listOfRows()); }
    @Transactional public Map<String,Object> createSynonym(TrainingRequests.Synonym r) {
        jdbc.sql("INSERT INTO semantic_synonym(domain,business_term,synonyms,target_expression,status) VALUES(?,?,?,?,'PUBLISHED')")
                .params(r.domain(),r.businessTerm(),r.synonyms(),r.targetExpression()).update();
        return Map.of("created",true);
    }

    public List<Map<String,Object>> examples() { return DatabaseRows.normalize(jdbc.sql("SELECT id,domain,question,sql_text,explanation,status,version,hit_count,created_at FROM sql_example ORDER BY id DESC").query().listOfRows()); }
    @Transactional public Map<String,Object> createExample(TrainingRequests.SqlExample r) {
        jdbc.sql("INSERT INTO sql_example(domain,question,sql_text,explanation,status) VALUES(?,?,?,?,'DRAFT')")
                .params(r.domain(),r.question(),r.sql(),r.explanation()).update();
        return Map.of("created",true,"status","DRAFT");
    }
    @Transactional public Map<String,Object> publishExample(long id) {
        int n=jdbc.sql("UPDATE sql_example SET status='PUBLISHED',version=version+1 WHERE id=?").param(id).update();
        return Map.of("id",id,"published",n==1);
    }

    public List<SqlExampleMatch> relevantExamples(String domain,String question,int limit) {
        String expanded=expandSynonyms(domain,question);
        return jdbc.sql("SELECT id,question,sql_text,explanation FROM sql_example WHERE domain=? AND status='PUBLISHED'")
                .param(domain).query((rs,row)->new SqlExampleMatch(rs.getLong("id"),rs.getString("question"),rs.getString("sql_text"),rs.getString("explanation"),similarity(expanded,expandSynonyms(domain,rs.getString("question")))))
                .list().stream().sorted(Comparator.comparingDouble(SqlExampleMatch::score).reversed()).limit(limit).toList();
    }

    public String expandSynonyms(String domain,String input) {
        String result=normalize(input);
        for (Map<String,Object> row: DatabaseRows.normalize(jdbc.sql("SELECT business_term,synonyms FROM semantic_synonym WHERE domain=? AND status='PUBLISHED'").param(domain).query().listOfRows())) {
            String term=String.valueOf(row.get("business_term"));
            for(String synonym:String.valueOf(row.get("synonyms")).split(",")) if(!synonym.isBlank() && result.contains(normalize(synonym))) result += normalize(term);
        }
        return result;
    }

    public List<Map<String,Object>> goldenQuestions() { return DatabaseRows.normalize(jdbc.sql("SELECT * FROM golden_question ORDER BY id DESC").query().listOfRows()); }
    @Transactional public Map<String,Object> createGolden(TrainingRequests.GoldenQuestion r) {
        jdbc.sql("INSERT INTO golden_question(domain,question,expected_sql,expected_result_json,status) VALUES(?,?,?,?,'ENABLED')")
                .params(r.domain(),r.question(),r.expectedSql(),r.expectedResultJson()).update();
        return Map.of("created",true);
    }
    public Map<String,Object> golden(long id) { return DatabaseRows.normalize(jdbc.sql("SELECT * FROM golden_question WHERE id=?").param(id).query().singleRow()); }
    @Transactional public void saveEvaluation(long id,String status,double score,String detail) {
        jdbc.sql("UPDATE golden_question SET last_run_status=?,last_score=?,last_detail=?,last_run_at=CURRENT_TIMESTAMP WHERE id=?")
                .params(status,score,detail,id).update();
    }

    public List<Map<String,Object>> promptContext(String domain,String question) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(SqlExampleMatch x:relevantExamples(domain,question,3)) result.add(Map.of("question",x.question(),"sql",x.sql(),"score",x.score()));
        return result;
    }

    public List<Map<String,Object>> feedback() {
        return DatabaseRows.normalize(jdbc.sql("SELECT f.id,f.query_run_id,q.question,q.generated_sql,f.rating,f.feedback_comment AS \"comment\",f.corrected_sql,f.created_at FROM query_feedback f JOIN query_run q ON q.id=f.query_run_id ORDER BY f.id DESC")
                .query().listOfRows());
    }

    @Transactional public Map<String,Object> promoteFeedback(long id) {
        Map<String,Object> row=DatabaseRows.normalize(jdbc.sql("SELECT q.question,q.generated_sql,f.corrected_sql FROM query_feedback f JOIN query_run q ON q.id=f.query_run_id WHERE f.id=?")
                .param(id).query().singleRow());
        String corrected=row.get("corrected_sql")==null?"":String.valueOf(row.get("corrected_sql"));
        String sql=corrected.isBlank()?String.valueOf(row.get("generated_sql")):corrected;
        jdbc.sql("INSERT INTO sql_example(domain,question,sql_text,explanation,status) VALUES('sales',?,?,?,'DRAFT')")
                .params(String.valueOf(row.get("question")),sql,"由用户反馈 #"+id+" 转换").update();
        return Map.of("promoted",true,"status","DRAFT");
    }

    private long count(String table) { return jdbc.sql("SELECT COUNT(*) FROM "+table).query(Long.class).single(); }
    private String normalize(String text) { return text==null?"":text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]",""); }
    private double similarity(String a,String b) {
        if(a.equals(b)) return 1;
        Set<String> aa=grams(a),bb=grams(b); if(aa.isEmpty()||bb.isEmpty()) return 0;
        Set<String> both=new HashSet<>(aa); both.retainAll(bb); Set<String> all=new HashSet<>(aa); all.addAll(bb);
        return (double)both.size()/all.size();
    }
    private Set<String> grams(String s) { Set<String> x=new HashSet<>(); for(int i=0;i<Math.max(0,s.length()-1);i++) x.add(s.substring(i,i+2)); return x; }
}
