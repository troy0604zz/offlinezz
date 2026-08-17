package com.example.aibi.common;

import java.sql.Clob;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Keeps map-shaped JDBC responses stable across H2 and Oracle drivers. */
public final class DatabaseRows {
    private DatabaseRows() {
    }

    public static List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
        return rows.stream().map(DatabaseRows::normalize).toList();
    }

    public static Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> result.put(key.toLowerCase(Locale.ROOT), normalizeValue(value)));
        return result;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Clob clob) {
            try {
                return clob.getSubString(1, Math.toIntExact(clob.length()));
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to read Oracle CLOB", ex);
            }
        }
        return value;
    }
}
