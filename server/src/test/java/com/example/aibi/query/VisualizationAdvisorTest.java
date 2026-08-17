package com.example.aibi.query;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class VisualizationAdvisorTest {
    private final VisualizationAdvisor advisor = new VisualizationAdvisor();

    @Test
    void recommendsKpiCardsForSingleSummaryRow() {
        QueryAnswer.ChartSpec spec = advisor.advise(List.of(row(
                "net_sales", new BigDecimal("890000.00"),
                "gross_margin", new BigDecimal("0.36")
        )));

        assertThat(spec.type()).isEqualTo("kpi");
        assertThat(spec.valueFields()).containsExactly("net_sales", "gross_margin");
    }

    @Test
    void recommendsLineChartForTemporalSeries() {
        QueryAnswer.ChartSpec spec = advisor.advise(List.of(
                row("month_no", 1, "net_sales", new BigDecimal("890000")),
                row("month_no", 2, "net_sales", new BigDecimal("760000"))
        ));

        assertThat(spec.type()).isEqualTo("line");
        assertThat(spec.categoryField()).isEqualTo("month_no");
        assertThat(spec.valueFields()).containsExactly("net_sales");
    }

    @Test
    void recommendsHorizontalBarForSmallCategoryComparison() {
        QueryAnswer.ChartSpec spec = advisor.advise(List.of(
                row("customer_name", "A 客户", "net_sales", 900),
                row("customer_name", "B 客户", "net_sales", 700)
        ));

        assertThat(spec.type()).isEqualTo("bar");
        assertThat(spec.categoryField()).isEqualTo("customer_name");
    }

    @Test
    void declinesChartForTextOnlyDetails() {
        QueryAnswer.ChartSpec spec = advisor.advise(List.of(
                row("customer_name", "A 客户", "status", "ACTIVE"),
                row("customer_name", "B 客户", "status", "INACTIVE")
        ));

        assertThat(spec.type()).isEqualTo("none");
    }

    @Test
    void declinesChartForTooManyCategories() {
        List<Map<String, Object>> rows = IntStream.rangeClosed(1, 21)
                .mapToObj(index -> row("product_name", "产品 " + index, "sales", index * 100))
                .toList();

        QueryAnswer.ChartSpec spec = advisor.advise(rows);

        assertThat(spec.type()).isEqualTo("none");
        assertThat(spec.reason()).contains("20");
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
