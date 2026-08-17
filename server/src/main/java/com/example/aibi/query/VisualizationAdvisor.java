package com.example.aibi.query;

import org.springframework.stereotype.Component;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Selects a presentation from query metadata and values without delegating the decision to the LLM.
 * A conservative "none" result is preferable to a misleading chart.
 */
@Component
public class VisualizationAdvisor {
    private static final int MAX_BAR_CATEGORIES = 20;
    private static final int MAX_LINE_POINTS = 120;
    private static final int MAX_SERIES = 3;
    private static final int MAX_KPIS = 6;

    private static final Set<String> TEMPORAL_TOKENS = Set.of(
            "date", "time", "day", "week", "month", "quarter", "year", "period",
            "日期", "时间", "日", "周", "月", "季度", "年", "期间"
    );
    private static final Pattern YEAR_MONTH = Pattern.compile("^\\d{4}[-/.年](0?[1-9]|1[0-2])(?:月)?$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}[-/.年](0?[1-9]|1[0-2])[-/.月](0?[1-9]|[12]\\d|3[01])(?:日)?$");
    private static final Pattern QUARTER = Pattern.compile("^(?:\\d{4}[- ]?)?[Qq][1-4]$|^\\d{4}年第?[一二三四1234]季度$");

    public QueryAnswer.ChartSpec advise(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0).isEmpty()) {
            return none("没有可用于可视化的数据");
        }

        List<String> fields = new ArrayList<>(rows.get(0).keySet());
        List<String> numericFields = fields.stream()
                .filter(field -> isNumericColumn(rows, field))
                .toList();

        if (rows.size() == 1) {
            if (numericFields.isEmpty()) {
                return none("单行结果不包含数值指标，使用数据表格更准确");
            }
            String contextField = fields.stream().filter(field -> !numericFields.contains(field)).findFirst().orElse(null);
            return new QueryAnswer.ChartSpec(
                    "kpi", "关键指标", contextField,
                    numericFields.stream().limit(MAX_KPIS).toList(),
                    "单行汇总结果适合使用指标卡展示"
            );
        }

        String temporalField = fields.stream()
                .filter(field -> isTemporalColumn(rows, field))
                .findFirst().orElse(null);
        String categoryField = temporalField != null ? temporalField : fields.stream()
                .filter(field -> !numericFields.contains(field))
                .findFirst().orElse(null);

        if (categoryField == null) {
            return none("结果中没有可识别的时间或分类维度");
        }

        List<String> valueFields = numericFields.stream()
                .filter(field -> !field.equals(categoryField))
                .limit(MAX_SERIES)
                .toList();
        if (valueFields.isEmpty()) {
            return none("分类维度之外没有可绘制的数值指标");
        }

        if (temporalField != null) {
            if (rows.size() > MAX_LINE_POINTS) {
                return none("时间序列超过 120 个点，表格展示可避免过度压缩");
            }
            return new QueryAnswer.ChartSpec(
                    "line", "趋势分析", categoryField, valueFields,
                    "识别到时间维度和数值指标，使用趋势图展示"
            );
        }

        if (rows.size() <= MAX_BAR_CATEGORIES) {
            return new QueryAnswer.ChartSpec(
                    "bar", "分类对比", categoryField, valueFields,
                    "分类数量适中，使用横向条形图便于比较"
            );
        }

        return none("分类超过 20 项，强制绘图会降低可读性");
    }

    private QueryAnswer.ChartSpec none(String reason) {
        return new QueryAnswer.ChartSpec("none", "", null, List.of(), reason);
    }

    private boolean isNumericColumn(List<Map<String, Object>> rows, String field) {
        boolean foundValue = false;
        for (Map<String, Object> row : rows) {
            Object value = row.get(field);
            if (value == null) continue;
            foundValue = true;
            if (!(value instanceof Number)) return false;
        }
        return foundValue;
    }

    private boolean isTemporalColumn(List<Map<String, Object>> rows, String field) {
        String normalized = field.toLowerCase(Locale.ROOT);
        if (TEMPORAL_TOKENS.stream().anyMatch(normalized::contains)) return true;

        int checked = 0;
        for (Map<String, Object> row : rows) {
            Object value = row.get(field);
            if (value == null) continue;
            checked++;
            if (value instanceof TemporalAccessor || value instanceof java.util.Date) continue;
            String text = String.valueOf(value).trim();
            if (!YEAR_MONTH.matcher(text).matches()
                    && !DATE.matcher(text).matches()
                    && !QUARTER.matcher(text).matches()) return false;
            if (checked >= 12) break;
        }
        return checked > 0;
    }
}
