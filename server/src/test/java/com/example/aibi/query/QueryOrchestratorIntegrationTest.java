package com.example.aibi.query;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueryOrchestratorIntegrationTest {
    @Autowired QueryOrchestrator orchestrator;
    @Autowired QueryExportService exports;

    @Test
    void executesMonthlyEastChinaNetSalesWithMockAi() {
        QueryAnswer answer = orchestrator.ask(new AskRequest("查询2026年华东区域按月净销售额", "sales"));

        assertThat(answer.status()).isEqualTo("COMPLETED");
        assertThat(answer.llmProvider()).isEqualTo("deterministic-mock");
        assertThat(answer.rows()).hasSize(3);
        assertThat(answer.chart().type()).isEqualTo("line");
        assertThat(answer.chart().categoryField()).isEqualTo("month_no");
        assertThat(answer.chart().valueFields()).containsExactly("net_sales");
        assertThat(new BigDecimal(answer.rows().get(0).get("net_sales").toString()))
                .isEqualByComparingTo("890000.00");
        assertThat(answer.tables()).contains("sales_order", "sales_order_item", "refund", "customer", "region");
    }

    @Test
    void exportsConfirmedQuerySnapshotAsExcelCsvAndXml() {
        QueryAnswer answer = orchestrator.ask(new AskRequest("查询2026年华东区域按月净销售额", "sales"));
        orchestrator.feedback(answer.queryRunId(), 5, "结果正确", null);

        QueryExportService.ExportFile xlsx = exports.export(answer.queryRunId(), "xlsx");
        QueryExportService.ExportFile csv = exports.export(answer.queryRunId(), "csv");
        QueryExportService.ExportFile xml = exports.export(answer.queryRunId(), "xml");

        assertThat(xlsx.contentType()).contains("spreadsheetml");
        assertThat(xlsx.content()).startsWith((byte) 'P', (byte) 'K');
        assertThat(new String(csv.content(), StandardCharsets.UTF_8))
                .startsWith("\uFEFF\"").contains("month_no").contains("net_sales");
        assertThat(new String(xml.content(), StandardCharsets.UTF_8))
                .contains("<queryReport>").contains("name=\"net_sales\"");
    }
}
