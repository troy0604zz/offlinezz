package com.example.aibi.domain;

import com.example.aibi.auth.CurrentUserProvider;
import com.example.aibi.common.BusinessException;
import com.example.aibi.common.DatabaseRows;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class DataDomainService {
    private final JdbcClient jdbc;
    private final CurrentUserProvider currentUser;
    private final DomainAccessService access;

    public DataDomainService(JdbcClient jdbc, CurrentUserProvider currentUser, DomainAccessService access) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.access = access;
    }

    public List<Map<String, Object>> mine() {
        if ("system".equals(currentUser.username())) {
            return DatabaseRows.normalize(jdbc.sql("SELECT code,name,description,status,created_by,created_at,updated_at FROM data_domain ORDER BY name").query().listOfRows());
        }
        return DatabaseRows.normalize(jdbc.sql("""
                SELECT d.code,d.name,d.description,d.status,d.created_by,d.created_at,d.updated_at,
                       m.can_query,m.can_report,m.can_train
                FROM data_domain d JOIN domain_member m ON m.domain_code=d.code
                WHERE m.user_id=? AND d.status='ACTIVE' ORDER BY d.name
                """).param(currentUser.userId()).query().listOfRows());
    }

    @Transactional
    public Map<String, Object> create(DomainRequest request) {
        String code = access.normalize(request.code());
        if (exists(code)) throw new BusinessException("DOMAIN_EXISTS", "数据域编码已存在：" + code, HttpStatus.CONFLICT);
        jdbc.sql("INSERT INTO data_domain(code,name,description,status,created_by) VALUES(?,?,?,'ACTIVE',?)")
                .params(code, required(request.name()), request.description(), currentUser.username()).update();
        jdbc.sql("INSERT INTO domain_data_source(domain_code,jdbc_url,driver_class,validation_query) VALUES(?,'UNCONFIGURED','oracle.jdbc.OracleDriver','SELECT 1 FROM DUAL')")
                .param(code).update();
        jdbc.sql("INSERT INTO domain_member(domain_code,user_id,can_query,can_report,can_train) VALUES(?,?,1,1,1)")
                .params(code, currentUser.userId()).update();
        return one(code);
    }

    @Transactional
    public Map<String, Object> update(String rawCode, DomainRequest request) {
        String code = access.requireTrain(rawCode);
        jdbc.sql("UPDATE data_domain SET name=?,description=?,updated_at=CURRENT_TIMESTAMP WHERE code=?")
                .params(required(request.name()), request.description(), code).update();
        return one(code);
    }

    public List<Map<String, Object>> members(String rawDomain) {
        String domain = access.requireTrain(rawDomain);
        return DatabaseRows.normalize(jdbc.sql("""
                SELECT u.id,u.username,u.display_name,m.can_query,m.can_report,m.can_train,m.created_at
                FROM domain_member m JOIN app_user u ON u.id=m.user_id
                WHERE m.domain_code=? ORDER BY u.username
                """).param(domain).query().listOfRows());
    }

    @Transactional
    public Map<String, Object> upsertMember(String rawDomain, MemberRequest request) {
        String domain = access.requireTrain(rawDomain);
        Long userId = jdbc.sql("SELECT id FROM app_user WHERE LOWER(username)=LOWER(?) AND enabled=1")
                .param(required(request.username())).query(Long.class).optional()
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在或已禁用", HttpStatus.NOT_FOUND));
        int changed = jdbc.sql("UPDATE domain_member SET can_query=?,can_report=?,can_train=? WHERE domain_code=? AND user_id=?")
                .params(request.canQuery(), request.canReport(), request.canTrain(), domain, userId).update();
        if (changed == 0) {
            jdbc.sql("INSERT INTO domain_member(domain_code,user_id,can_query,can_report,can_train) VALUES(?,?,?,?,?)")
                    .params(domain, userId, request.canQuery(), request.canReport(), request.canTrain()).update();
        }
        return Map.of("saved", true, "domain", domain, "username", request.username());
    }

    @Transactional
    public Map<String, Object> deleteMember(String rawDomain, long userId) {
        String domain = access.requireTrain(rawDomain);
        if (userId == currentUser.userId()) {
            throw new BusinessException("OWNER_SELF_REMOVE", "不能移除自己对当前数据域的管理权限", HttpStatus.BAD_REQUEST);
        }
        int changed = jdbc.sql("DELETE FROM domain_member WHERE domain_code=? AND user_id=?").params(domain, userId).update();
        return Map.of("deleted", changed == 1);
    }

    private Map<String, Object> one(String code) {
        return DatabaseRows.normalize(jdbc.sql("SELECT code,name,description,status,created_by,created_at,updated_at FROM data_domain WHERE code=?")
                .param(code).query().singleRow());
    }
    private boolean exists(String code) { return jdbc.sql("SELECT COUNT(*) FROM data_domain WHERE code=?").param(code).query(Integer.class).single() > 0; }
    private String required(String value) {
        if (value == null || value.isBlank()) throw new BusinessException("VALIDATION_FAILED", "数据域名称不能为空", HttpStatus.BAD_REQUEST);
        return value.trim();
    }

    public record DomainRequest(String code, String name, String description) {}
    public record MemberRequest(String username, boolean canQuery, boolean canReport, boolean canTrain) {}
}
