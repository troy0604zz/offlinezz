package com.example.aibi.query;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record QueryAnswer(String queryRunId, String question, String status, String sql, String explanation,
                          List<String> assumptions, double confidence, Set<String> tables,
                          List<Map<String, Object>> rows, ChartSpec chart, String answer,
                          String llmProvider, String vectorProvider, long elapsedMs) {
    public record ChartSpec(String type, String title, String categoryField,
                            List<String> valueFields, String reason) {}
}
