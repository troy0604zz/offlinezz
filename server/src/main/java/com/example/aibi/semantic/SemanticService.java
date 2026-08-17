package com.example.aibi.semantic;

import com.example.aibi.common.DatabaseRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class SemanticService {
    private final JdbcClient jdbc;

    public SemanticService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> metrics() {
        return DatabaseRows.normalize(jdbc.sql("SELECT id,code,name,description,expression_sql,base_table,status,version,created_at FROM semantic_metric ORDER BY code")
                .query().listOfRows());
    }

    public List<Map<String, Object>> relations() {
        return DatabaseRows.normalize(jdbc.sql("SELECT * FROM semantic_relation ORDER BY left_table,right_table").query().listOfRows());
    }

    @Transactional
    public Map<String,Object> createRelation(com.example.aibi.training.TrainingRequests.Relation request) {
        jdbc.sql("INSERT INTO semantic_relation(left_table,right_table,join_type,join_condition,cardinality,enabled) VALUES(?,?,?,?,?,?)")
                .params(request.leftTable(),request.rightTable(),request.joinType(),request.joinCondition(),request.cardinality(),true).update();
        return Map.of("created",true);
    }

    @Transactional
    public Map<String, Object> createMetric(MetricRequest request) {
        jdbc.sql("INSERT INTO semantic_metric(code,name,description,expression_sql,base_table,status,version) VALUES(?,?,?,?,?,'DRAFT',1)")
                .params(request.code(), request.name(), request.description(), request.expressionSql(), request.baseTable())
                .update();
        Long id = jdbc.sql("SELECT id FROM semantic_metric WHERE code=?").param(request.code()).query(Long.class).single();
        return Map.of("id", id, "code", request.code(), "status", "DRAFT", "version", 1);
    }

    @Transactional
    public Map<String, Object> publish(long id) {
        int changed = jdbc.sql("UPDATE semantic_metric SET status='PUBLISHED', version=version+1 WHERE id=?")
                .param(id).update();
        return Map.of("id", id, "published", changed == 1);
    }
}
