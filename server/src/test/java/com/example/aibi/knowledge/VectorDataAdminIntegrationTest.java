package com.example.aibi.knowledge;

import com.example.aibi.auth.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VectorDataAdminIntegrationTest {
    private static final String DOMAIN = "purge_test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void purgeSupportsPreviewTypeAndDomainWhileKeepingOracleAndVectorsSynchronized() throws Exception {
        String token = login();
        createDomain(token);
        createAssets(token);

        mvc.perform(get("/api/v1/admin/vector-data/purge-preview")
                        .param("domain", DOMAIN).param("assetType", "DOCUMENT")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oracleMatched.DOCUMENT").value(1))
                .andExpect(jsonPath("$.requiredConfirmation").value(VectorDataAdminService.CONFIRMATION));

        mvc.perform(post("/api/v1/admin/vector-data/purge")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("domain", DOMAIN, "assetType", "METRIC",
                                "confirmation", "wrong"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PURGE_CONFIRMATION_REQUIRED"));

        mvc.perform(post("/api/v1/admin/vector-data/purge")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("domain", DOMAIN, "assetType", "METRIC",
                                "confirmation", VectorDataAdminService.CONFIRMATION))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oracleDeleted.METRIC").value(1))
                .andExpect(jsonPath("$.preservedOracleTypes[0]").value("SEMANTIC_RELATION"));

        mvc.perform(get("/api/v1/admin/semantic/metrics").param("domain", DOMAIN)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        String afterMetricPurge = search(token, "purge_metric_marker");
        assertThat(afterMetricPurge).doesNotContain("METRIC").doesNotContain("purge_metric_marker");
        mvc.perform(get("/api/v1/admin/training/schemas").param("domain", DOMAIN)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Purge schema"));

        mvc.perform(post("/api/v1/admin/vector-data/purge")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("domain", DOMAIN, "assetType", "ALL",
                                "confirmation", VectorDataAdminService.CONFIRMATION))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oracleDeleted.DOCUMENT").value(1))
                .andExpect(jsonPath("$.oracleDeleted.SCHEMA").value(1))
                .andExpect(jsonPath("$.oracleDeleted.SQL_EXAMPLE").value(1));

        mvc.perform(get("/api/v1/admin/knowledge/documents").param("domain", DOMAIN)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/admin/training/schemas").param("domain", DOMAIN)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/admin/training/sql-examples").param("domain", DOMAIN)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertThat(search(token, "purge_schema_marker")).isEqualTo("[]");
    }

    private void createDomain(String token) throws Exception {
        mvc.perform(post("/api/v1/admin/domains").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", DOMAIN, "name", "Purge Test Domain",
                                "description", "isolated vector purge integration test"))))
                .andExpect(status().isOk());
    }

    private void createAssets(String token) throws Exception {
        mvc.perform(post("/api/v1/admin/training/schemas").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("domain", DOMAIN, "name", "Purge schema",
                                "dialect", "Oracle 19c", "ddlText", "CREATE TABLE purge_schema_marker(id NUMBER)",
                                "description", "purge_schema_marker"))))
                .andExpect(status().isOk());

        String metric = mvc.perform(post("/api/v1/admin/semantic/metrics").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("domain", DOMAIN, "code", "purge_metric",
                                "name", "purge_metric_marker", "description", "metric purge marker",
                                "expressionSql", "COUNT(*)", "baseTable", "purge_schema_marker"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long metricId = mapper.readTree(metric).path("id").asLong();
        mvc.perform(post("/api/v1/admin/semantic/metrics/{id}/publish", metricId).param("domain", DOMAIN)
                        .header("Authorization", bearer(token))).andExpect(status().isOk());

        String example = mvc.perform(post("/api/v1/admin/training/sql-examples")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("domain", DOMAIN, "question", "purge_sql_marker",
                                "sql", "SELECT COUNT(*) FROM purge_schema_marker", "explanation", "purge example"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long exampleId = mapper.readTree(example).path("id").asLong();
        mvc.perform(post("/api/v1/admin/training/sql-examples/{id}/publish", exampleId).param("domain", DOMAIN)
                        .header("Authorization", bearer(token))).andExpect(status().isOk());

        MockMultipartFile file = new MockMultipartFile("file", "purge-doc.md", "text/markdown",
                "purge_document_marker".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/admin/knowledge/documents").file(file).param("domain", DOMAIN)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private String search(String token, String query) throws Exception {
        return mvc.perform(get("/api/v1/admin/knowledge/search").param("domain", DOMAIN)
                        .param("query", query).param("topK", "20").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String login() throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest("ai_admin", "Aibi@123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(response);
        return json.path("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }
}
