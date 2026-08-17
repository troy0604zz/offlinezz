package com.example.aibi.query;

import com.example.aibi.common.BusinessException;
import com.example.aibi.common.DatabaseRows;
import com.example.aibi.config.AiBiProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SafeQueryExecutor {
    private final JdbcTemplate jdbc;
    private final AiBiProperties properties;

    public SafeQueryExecutor(JdbcTemplate jdbc, AiBiProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public List<Map<String, Object>> execute(SqlGuard.ValidationResult validation) {
        try {
            List<Map<String, Object>> rows = jdbc.query(connection -> {
                var statement = connection.prepareStatement(validation.sql());
                statement.setQueryTimeout(properties.query().timeoutSeconds());
                statement.setMaxRows(validation.maxRows());
                statement.setFetchSize(Math.min(validation.maxRows(), 200));
                return statement;
            }, resultSet -> {
                List<Map<String, Object>> resultRows = new ArrayList<>();
                ResultSetMetaData meta = resultSet.getMetaData();
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), resultSet.getObject(i));
                    }
                    resultRows.add(row);
                }
                return resultRows;
            });
            return DatabaseRows.normalize(rows);
        } catch (Exception ex) {
            Throwable root = ex;
            while (root.getCause() != null) root = root.getCause();
            throw new BusinessException("SQL_EXECUTION_FAILED", "查询执行失败：" + root.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
