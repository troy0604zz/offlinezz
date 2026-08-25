package com.example.aibi.domain;

import com.example.aibi.auth.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MultiDomainIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void trainerCreatesIsolatedDomainAndMaintainsTrainingAssets() throws Exception {
        String admin = login("ai_admin");
        String code = "finance_test";
        mvc.perform(post("/api/v1/admin/domains").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", "财务测试域", "description", "隔离测试"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));

        mvc.perform(get("/api/v1/admin/domains/{code}/datasource", code).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jdbcUrl").value("UNCONFIGURED"));

        mvc.perform(post("/api/v1/admin/semantic/metrics").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                                "domain", code, "code", "net_sales", "name", "财务净额",
                                "description", "允许与销售域使用相同业务编码", "expressionSql", "SUM(amount)",
                                "baseTable", "finance_ledger"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("net_sales"));

        String created = mvc.perform(post("/api/v1/admin/training/schemas").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                                "domain", code, "name", "财务表", "dialect", "Oracle 19c",
                                "ddlText", "CREATE TABLE finance_ledger(id NUMBER, amount NUMBER)",
                                "description", "财务流水"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).path("id").asLong();

        mvc.perform(put("/api/v1/admin/training/schemas/{id}", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                                "domain", code, "name", "财务流水表", "dialect", "Oracle 19c",
                                "ddlText", "CREATE TABLE finance_ledger(id NUMBER, amount NUMBER, booked_at DATE)",
                                "description", "更新后的财务流水"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(true));

        mvc.perform(get("/api/v1/admin/training/schemas").header("Authorization", bearer(admin)).param("domain", code))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("财务流水表"));

        String questionUser = login("question_user");
        String domains = mvc.perform(get("/api/v1/domains").header("Authorization", bearer(questionUser)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(domains).doesNotContain(code);

        mvc.perform(delete("/api/v1/admin/training/schemas/{id}", id).header("Authorization", bearer(admin)).param("domain", code))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true));
    }

    @Test
    void reportIsPlannedFromRequestAndHistoryDetailIsReadable() throws Exception {
        String token = login("report_user");
        String uniqueRequest = "生成2026年华东区域客户净销售额排名分析";
        String response = mvc.perform(post("/api/v1/reports/generate").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                                "title", "动态客户分析", "request", uniqueRequest, "knowledgeDomain", "sales"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request").value(uniqueRequest))
                .andExpect(jsonPath("$.sections[0].question").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(response).path("id").asText();

        mvc.perform(get("/api/v1/reports/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.sections[0].query.sql").isNotEmpty());
    }

    private String login(String username) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(username, "Aibi@123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("accessToken").asText();
    }
    private String bearer(String token) { return "Bearer " + token; }
    private String json(Object value) throws Exception { return mapper.writeValueAsString(value); }
}
