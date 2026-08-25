package com.example.aibi.semantic;

import com.example.aibi.common.DatabaseRows;
import com.example.aibi.domain.DomainAccessService;
import com.example.aibi.knowledge.KnowledgeService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class SemanticService {
    private final JdbcClient jdbc;
    private final DomainAccessService access;
    private final KnowledgeService knowledge;

    public SemanticService(JdbcClient jdbc, DomainAccessService access,KnowledgeService knowledge) {
        this.jdbc = jdbc; this.access = access; this.knowledge=knowledge;
    }

    public List<Map<String, Object>> metrics(String rawDomain) {
        String domain = access.requireTrain(rawDomain);
        return DatabaseRows.normalize(jdbc.sql("SELECT id,domain,business_code AS code,name,description,expression_sql,base_table,status,version,created_at FROM semantic_metric WHERE domain=? ORDER BY business_code")
                .param(domain).query().listOfRows());
    }

    public List<Map<String, Object>> metricsForQuery(String rawDomain) {
        String domain = access.normalize(rawDomain);
        return DatabaseRows.normalize(jdbc.sql("SELECT id,domain,business_code AS code,name,description,expression_sql,base_table,status,version,created_at FROM semantic_metric WHERE domain=? AND status='PUBLISHED' ORDER BY business_code")
                .param(domain).query().listOfRows());
    }

    public List<Map<String, Object>> relations(String rawDomain) {
        String domain = access.requireTrain(rawDomain);
        return relationsForQuery(domain);
    }

    public List<Map<String, Object>> relationsForQuery(String rawDomain) {
        String domain = access.normalize(rawDomain);
        return DatabaseRows.normalize(jdbc.sql("SELECT * FROM semantic_relation WHERE domain=? ORDER BY left_table,right_table")
                .param(domain).query().listOfRows());
    }

    @Transactional
    public Map<String,Object> createRelation(com.example.aibi.training.TrainingRequests.Relation request) {
        String domain = access.requireTrain(request.domain());
        jdbc.sql("INSERT INTO semantic_relation(domain,left_table,right_table,join_type,join_condition,cardinality,enabled) VALUES(?,?,?,?,?,?,?)")
                .params(domain,request.leftTable(),request.rightTable(),request.joinType(),request.joinCondition(),request.cardinality(),true).update();
        Long id = jdbc.sql("SELECT MAX(id) FROM semantic_relation WHERE domain=?").param(domain).query(Long.class).single();
        return Map.of("id",id,"created",true);
    }

    @Transactional
    public Map<String, Object> createMetric(MetricRequest request) {
        String domain = access.requireTrain(request.domain());
        jdbc.sql("INSERT INTO semantic_metric(domain,code,business_code,name,description,expression_sql,base_table,status,version) VALUES(?,?,?,?,?,?,?,'DRAFT',1)")
                .params(domain, "m_"+java.util.UUID.randomUUID(), request.code(), request.name(), request.description(), request.expressionSql(), request.baseTable())
                .update();
        Long id = jdbc.sql("SELECT id FROM semantic_metric WHERE domain=? AND business_code=?").params(domain, request.code()).query(Long.class).single();
        return Map.of("id", id, "code", request.code(), "status", "DRAFT", "version", 1);
    }

    @Transactional
    public Map<String, Object> updateMetric(long id, MetricRequest request) {
        String domain = access.requireTrain(request.domain());
        requireOwned("semantic_metric", id, domain);
        jdbc.sql("UPDATE semantic_metric SET business_code=?,name=?,description=?,expression_sql=?,base_table=?,status='DRAFT',version=version+1 WHERE id=? AND domain=?")
                .params(request.code(),request.name(),request.description(),request.expressionSql(),request.baseTable(),id,domain).update();
        knowledge.rebuildDomain(domain);
        return Map.of("id",id,"updated",true);
    }

    @Transactional
    public Map<String,Object> updateRelation(long id, com.example.aibi.training.TrainingRequests.Relation request) {
        String domain=access.requireTrain(request.domain()); requireOwned("semantic_relation",id,domain);
        jdbc.sql("UPDATE semantic_relation SET left_table=?,right_table=?,join_type=?,join_condition=?,cardinality=? WHERE id=? AND domain=?")
                .params(request.leftTable(),request.rightTable(),request.joinType(),request.joinCondition(),request.cardinality(),id,domain).update();
        return Map.of("id",id,"updated",true);
    }

    @Transactional
    public Map<String,Object> deleteMetric(long id,String rawDomain) {
        String domain=access.requireTrain(rawDomain);requireOwned("semantic_metric",id,domain);
        int changed=jdbc.sql("DELETE FROM semantic_metric WHERE id=? AND domain=?").params(id,domain).update();
        knowledge.rebuildDomain(domain);
        return Map.of("id",id,"deleted",changed==1);
    }
    @Transactional
    public Map<String,Object> deleteRelation(long id,String rawDomain) { return delete("semantic_relation",id,access.requireTrain(rawDomain)); }

    @Transactional
    public Map<String, Object> publish(long id, String rawDomain) {
        String domain=access.requireTrain(rawDomain); requireOwned("semantic_metric",id,domain);
        int changed = jdbc.sql("UPDATE semantic_metric SET status='PUBLISHED', version=version+1 WHERE id=? AND domain=?")
                .params(id,domain).update();
        knowledge.rebuildDomain(domain);
        return Map.of("id", id, "published", changed == 1);
    }

    private void requireOwned(String table,long id,String domain) {
        if(jdbc.sql("SELECT COUNT(*) FROM "+table+" WHERE id=? AND domain=?").params(id,domain).query(Integer.class).single()==0)
            throw new com.example.aibi.common.BusinessException("RESOURCE_NOT_FOUND","域内训练资产不存在",org.springframework.http.HttpStatus.NOT_FOUND);
    }
    private Map<String,Object> delete(String table,long id,String domain) {
        requireOwned(table,id,domain); int changed=jdbc.sql("DELETE FROM "+table+" WHERE id=? AND domain=?").params(id,domain).update();
        return Map.of("id",id,"deleted",changed==1);
    }
}
