package com.example.aibi.domain;

import com.example.aibi.common.BusinessException;
import com.example.aibi.common.DatabaseRows;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

@Service
public class DomainDataSourceService {
    public static final String PLATFORM = "PLATFORM";
    public static final String UNCONFIGURED = "UNCONFIGURED";

    private final JdbcClient jdbc;
    private final DataSource platformDataSource;
    private final DatasourceSecretCipher cipher;
    private final DomainAccessService access;

    public DomainDataSourceService(JdbcClient jdbc, DataSource platformDataSource, DatasourceSecretCipher cipher,
                                   DomainAccessService access) {
        this.jdbc = jdbc;
        this.platformDataSource = platformDataSource;
        this.cipher = cipher;
        this.access = access;
    }

    public Map<String, Object> get(String rawDomain) {
        String domain = access.requireTrain(rawDomain);
        Map<String, Object> row = config(domain);
        return Map.of(
                "domainCode", domain,
                "jdbcUrl", String.valueOf(row.get("jdbc_url")),
                "username", row.get("username") == null ? "" : String.valueOf(row.get("username")),
                "driverClass", String.valueOf(row.get("driver_class")),
                "validationQuery", String.valueOf(row.get("validation_query")),
                "passwordConfigured", row.get("password_cipher") != null && !String.valueOf(row.get("password_cipher")).isBlank(),
                "platformManaged", PLATFORM.equals(row.get("jdbc_url")));
    }

    @Transactional
    public Map<String, Object> update(String rawDomain, UpdateRequest request) {
        String domain = access.requireTrain(rawDomain);
        String url = required(request.jdbcUrl(), "JDBC URL 不能为空");
        if (!PLATFORM.equals(url) && !url.startsWith("jdbc:")) {
            throw new BusinessException("INVALID_JDBC_URL", "JDBC URL 必须以 jdbc: 开头", HttpStatus.BAD_REQUEST);
        }
        String encrypted = request.password() == null || request.password().isBlank()
                ? stringValue(config(domain).get("password_cipher")) : cipher.encrypt(request.password());
        jdbc.sql("UPDATE domain_data_source SET jdbc_url=?,username=?,password_cipher=?,driver_class=?,validation_query=?,updated_at=CURRENT_TIMESTAMP WHERE domain_code=?")
                .params(url, blankToNull(request.username()), encrypted,
                        defaultValue(request.driverClass(), "oracle.jdbc.OracleDriver"),
                        defaultValue(request.validationQuery(), "SELECT 1 FROM DUAL"), domain).update();
        return get(domain);
    }

    public Map<String, Object> test(String rawDomain) {
        String domain = access.requireTrain(rawDomain);
        Map<String, Object> row = config(domain);
        String validation = String.valueOf(row.get("validation_query"));
        try (Connection connection = open(domain); var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            statement.execute(validation);
            return Map.of("success", true, "message", "数据源连接成功", "database", connection.getMetaData().getDatabaseProductName());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("DATASOURCE_CONNECTION_FAILED", "数据源连接失败：" + rootMessage(ex), HttpStatus.BAD_REQUEST);
        }
    }

    public Connection open(String rawDomain) {
        String domain = access.normalize(rawDomain);
        Map<String, Object> row = config(domain);
        String url = String.valueOf(row.get("jdbc_url"));
        if (UNCONFIGURED.equals(url)) {
            throw new BusinessException("DATASOURCE_NOT_CONFIGURED", "数据域尚未配置数据源：" + domain, HttpStatus.BAD_REQUEST);
        }
        try {
            if (PLATFORM.equals(url)) return platformDataSource.getConnection();
            String driver = String.valueOf(row.get("driver_class"));
            if (!driver.isBlank()) Class.forName(driver);
            return DriverManager.getConnection(url, stringValue(row.get("username")), cipher.decrypt(stringValue(row.get("password_cipher"))));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("DATASOURCE_CONNECTION_FAILED", "无法连接数据域数据源：" + rootMessage(ex), HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> config(String domain) {
        List<Map<String, Object>> rows = jdbc.sql("SELECT domain_code,jdbc_url,username,password_cipher,driver_class,validation_query FROM domain_data_source WHERE domain_code=?")
                .param(domain).query().listOfRows();
        if (rows.isEmpty()) throw new BusinessException("DATASOURCE_NOT_FOUND", "数据域没有数据源配置：" + domain, HttpStatus.NOT_FOUND);
        return DatabaseRows.normalize(rows.get(0));
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw new BusinessException("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST);
        return value.trim();
    }
    private String defaultValue(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
    private String rootMessage(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error.getMessage(); }

    public record UpdateRequest(String jdbcUrl, String username, String password, String driverClass, String validationQuery) {}
}
