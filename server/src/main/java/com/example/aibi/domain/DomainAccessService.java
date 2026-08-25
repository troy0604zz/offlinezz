package com.example.aibi.domain;

import com.example.aibi.auth.CurrentUserProvider;
import com.example.aibi.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class DomainAccessService {
    private final JdbcClient jdbc;
    private final CurrentUserProvider currentUser;

    public DomainAccessService(JdbcClient jdbc, CurrentUserProvider currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    public String requireQuery(String domain) { return require(domain, "can_query"); }
    public String requireReport(String domain) { return require(domain, "can_report"); }
    public String requireTrain(String domain) { return require(domain, "can_train"); }

    public String normalize(String domain) {
        String value = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z][a-z0-9_-]{1,99}")) {
            throw new BusinessException("INVALID_DOMAIN", "数据域编码必须以字母开头，只能包含字母、数字、下划线和短横线", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String require(String rawDomain, String capabilityColumn) {
        String domain = normalize(rawDomain);
        if ("system".equals(currentUser.username())) return domain;
        Integer count = jdbc.sql("SELECT COUNT(*) FROM domain_member m JOIN data_domain d ON d.code=m.domain_code " +
                        "WHERE m.domain_code=? AND m.user_id=? AND m." + capabilityColumn + "=? AND d.status='ACTIVE'")
                .params(domain, currentUser.userId(), true).query(Integer.class).single();
        if (count == null || count == 0) {
            throw new BusinessException("DOMAIN_FORBIDDEN", "当前账号无权访问数据域：" + domain, HttpStatus.FORBIDDEN);
        }
        return domain;
    }
}
