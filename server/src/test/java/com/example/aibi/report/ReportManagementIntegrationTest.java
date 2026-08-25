package com.example.aibi.report;

import com.example.aibi.auth.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReportManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcClient jdbc;

    @Test
    void reportOwnerCanDownloadPdfAndWordThenDeleteHistory() throws Exception {
        String id = createReadyReport("report_user", "晶圆客户 360 分析报告");
        String token = login("report_user");
        try {
            mvc.perform(get("/api/v1/reports").param("domain", "sales").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == '" + id + "')].can_delete").value(true));

            MvcResult pdf = mvc.perform(get("/api/v1/reports/{id}/export", id).param("format", "pdf")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(pdf.getResponse().getContentType()).isEqualTo("application/pdf");
            assertThat(pdf.getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes());
            assertThat(pdf.getResponse().getContentAsByteArray().length).isGreaterThan(1_000);
            assertThat(pdf.getResponse().getHeader("Content-Disposition")).contains(".pdf");
            try (PDDocument document = Loader.loadPDF(pdf.getResponse().getContentAsByteArray())) {
                assertThat(new PDFTextStripper().getText(document))
                        .contains("晶圆客户 360 分析报告", "执行摘要", "行动建议");
            }

            MvcResult word = mvc.perform(get("/api/v1/reports/{id}/export", id).param("format", "docx")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(word.getResponse().getContentType())
                    .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            assertThat(word.getResponse().getContentAsByteArray()).startsWith(new byte[]{'P', 'K'});
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(word.getResponse().getContentAsByteArray()))) {
                String paragraphs = document.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
                assertThat(paragraphs).contains("晶圆客户 360 分析报告", "执行摘要", "行动建议");
            }

            Integer exported = jdbc.sql("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND event_type='REPORT_EXPORTED'")
                    .param(id).query(Integer.class).single();
            assertThat(exported).isEqualTo(2);

            mvc.perform(delete("/api/v1/reports/{id}", id).header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deleted").value(true));
            mvc.perform(get("/api/v1/reports/{id}", id).header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound());
            Integer deletedAudit = jdbc.sql("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND event_type='REPORT_DELETED'")
                    .param(id).query(Integer.class).single();
            assertThat(deletedAudit).isEqualTo(1);
        } finally {
            jdbc.sql("DELETE FROM report_job WHERE id=?").param(id).update();
        }
    }

    @Test
    void administratorCanDeleteAnotherUsersReport() throws Exception {
        String id = createReadyReport("report_user", "管理员清理测试报告");
        String token = login("ai_admin");
        try {
            mvc.perform(delete("/api/v1/reports/{id}", id).header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deleted").value(true));
        } finally {
            jdbc.sql("DELETE FROM report_job WHERE id=?").param(id).update();
        }
    }

    @Test
    void unfinishedReportCannotBeExported() throws Exception {
        String id = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO report_job(id,title,request_text,status,domain,created_by) VALUES(?,?,?,'FAILED','sales','report_user')")
                .params(id, "失败报告", "测试失败报告").update();
        try {
            mvc.perform(get("/api/v1/reports/{id}/export", id).param("format", "pdf")
                            .header("Authorization", bearer(login("report_user"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REPORT_NOT_READY"));
        } finally {
            jdbc.sql("DELETE FROM report_job WHERE id=?").param(id).update();
        }
    }

    private String createReadyReport(String creator, String title) throws Exception {
        String id = UUID.randomUUID().toString();
        Map<String, Object> query = Map.of(
                "answer", "华东客户收入同比增长 12.5%，其中先进制程产品贡献最大。",
                "sql", "SELECT customer_name, SUM(net_amount) net_sales FROM fact_wafer_sales GROUP BY customer_name",
                "rows", List.of(
                        Map.of("CUSTOMER_NAME", "星海半导体", "NET_SALES", 12800000, "YOY_RATE", 0.125),
                        Map.of("CUSTOMER_NAME", "远景芯科", "NET_SALES", 9600000, "YOY_RATE", 0.083)));
        Map<String, Object> report = Map.of(
                "id", id,
                "domain", "sales",
                "title", title,
                "request", "生成华东晶圆代工客户 360 分析",
                "executiveSummary", "华东区域整体经营稳健，重点客户和先进制程需求保持增长。",
                "sections", List.of(Map.of("title", "客户收入与增长", "question", "查询客户净销售额和同比", "query", query)),
                "recommendations", List.of("优先保障先进制程产能", "跟进高增长客户的下一季度需求"),
                "warnings", List.of(),
                "generatedAt", Instant.now().toString(),
                "generatedBy", creator);
        jdbc.sql("INSERT INTO report_job(id,title,request_text,status,content_json,domain,created_by) VALUES(?,?,?,'READY',?,?,?)")
                .params(id, title, "生成华东晶圆代工客户 360 分析", mapper.writeValueAsString(report), "sales", creator)
                .update();
        return id;
    }

    private String login(String username) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(username, "Aibi@123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(response);
        return json.path("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
