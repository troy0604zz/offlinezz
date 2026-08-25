package com.example.aibi.query;

import com.example.aibi.common.BusinessException;
import com.example.aibi.config.AiBiProperties;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqlGuard {
    private static final Pattern TABLE_PATTERN = Pattern.compile("(?i)\\b(?:from|join)\\s+([a-zA-Z_][a-zA-Z0-9_$.]*)");
    private static final Pattern CTE_PATTERN = Pattern.compile("(?i)\\b([a-zA-Z_][a-zA-Z0-9_$]*)\\s+AS\\s*\\(\\s*(?:SELECT|WITH)\\b");
    private static final Pattern DANGEROUS_FUNCTION = Pattern.compile("(?i)\\b(pg_sleep|sleep|benchmark|load_file|dblink|xp_cmdshell)\\s*\\(");
    private static final Set<String> ALLOWED = Set.of(
            "region", "customer", "product", "sales_order", "sales_order_item", "refund",
            "refund_by_order", "order_amount",
            "f360_geo", "f360_customer", "f360_application", "f360_technology_node", "f360_fab",
            "f360_process_route", "f360_product", "f360_sales_order", "f360_order_line",
            "f360_wafer_lot", "f360_wafer_output", "f360_yield_result", "f360_shipment",
            "f360_customer_forecast", "f360_capacity_plan", "f360_price_agreement",
            "f360_product_cost", "f360_inventory_snapshot", "f360_quality_incident",
            "f360_customer_interaction", "f360_design_win", "f360_npi_milestone",
            "f360_equipment_downtime", "f360_customer_score_snapshot", "f360_application_market");

    private final AiBiProperties properties;

    public SqlGuard(AiBiProperties properties) { this.properties = properties; }

    public ValidationResult validate(String sql) {
        return validate(sql, ALLOWED);
    }

    public ValidationResult validate(String sql, Set<String> allowedTables) {
        if (sql == null || sql.isBlank()) reject("SQL_EMPTY", "生成 SQL 为空");
        String normalized = sql.strip().replaceFirst(";+\\s*$", "");
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(normalized);
            if (statements.getStatements().size() != 1) reject("SQL_MULTI_STATEMENT", "只允许一条 SQL");
            Statement statement = statements.getStatements().get(0);
            if (!(statement instanceof Select)) reject("SQL_NOT_READ_ONLY", "只允许 SELECT 查询");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            reject("SQL_PARSE_FAILED", "SQL 无法解析：" + ex.getMessage());
        }
        if (DANGEROUS_FUNCTION.matcher(normalized).find()) reject("SQL_DANGEROUS_FUNCTION", "SQL 包含禁止函数");

        Set<String> tables = new LinkedHashSet<>();
        // FROM inside EXTRACT(... FROM column) is SQL grammar, not a table reference.
        // Remove those expressions before the conservative whitelist scan.
        String tableScanSql = normalized.replaceAll("(?is)EXTRACT\\s*\\([^)]*\\)", "");
        Matcher matcher = TABLE_PATTERN.matcher(tableScanSql);
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase(Locale.ROOT);
            if (table.contains(".")) table = table.substring(table.lastIndexOf('.') + 1);
            tables.add(table);
        }
        Set<String> cteNames = new LinkedHashSet<>();
        Matcher cteMatcher = CTE_PATTERN.matcher(tableScanSql);
        while (cteMatcher.find()) cteNames.add(cteMatcher.group(1).toLowerCase(Locale.ROOT));
        Set<String> unknown = new LinkedHashSet<>(tables);
        unknown.removeAll(allowedTables.stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet()));
        unknown.removeAll(cteNames);
        if (!unknown.isEmpty()) reject("SQL_UNKNOWN_TABLE", "SQL 使用了未授权对象：" + unknown);
        return new ValidationResult(normalized, tables, properties.query().maxLimit());
    }

    private void reject(String code, String message) {
        throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }

    public record ValidationResult(String sql, Set<String> tables, int maxRows) {}
}
