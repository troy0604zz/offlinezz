package com.example.aibi.knowledge;

import com.example.aibi.auth.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeDeletionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void deletingDocumentRemovesDatabaseRecordFileAndSearchableVector() throws Exception {
        String token=login();
        String marker="knowledge_delete_probe_8f3b2a";
        MockMultipartFile file=new MockMultipartFile("file","delete-probe.md","text/markdown",
                ("# Delete probe\n"+marker+" only exists in this temporary document.").getBytes(StandardCharsets.UTF_8));
        String upload=mvc.perform(multipart("/api/v1/admin/knowledge/documents")
                        .file(file).param("domain","sales").header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.chunks").value(1))
                .andReturn().getResponse().getContentAsString();
        long id=mapper.readTree(upload).path("id").asLong();

        String before=mvc.perform(get("/api/v1/admin/knowledge/search").param("domain","sales")
                        .param("query",marker).param("topK","20").header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(before).contains(marker);

        mvc.perform(delete("/api/v1/admin/knowledge/documents/{id}",id).param("domain","sales")
                        .header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.vectorIndexRebuilt").value(true))
                .andExpect(jsonPath("$.storageFileDeleted").value(true));

        String after=mvc.perform(get("/api/v1/admin/knowledge/search").param("domain","sales")
                        .param("query",marker).param("topK","20").header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(after).doesNotContain(marker);
        mvc.perform(get("/api/v1/admin/knowledge/documents").param("domain","sales")
                        .header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == "+id+")]").isEmpty());
    }

    @Test
    void publishedMetricAndSqlQuestionBecomeVersionedSearchProjections() throws Exception {
        String token=login();
        String metricMarker="probe_metric_semantic_61ac";
        String metricResponse=mvc.perform(post("/api/v1/admin/semantic/metrics")
                        .header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("domain","sales","code","probe_metric_61ac",
                                "name",metricMarker,"description","unique metric retrieval marker",
                                "expressionSql","SUM(amount)","baseTable","sales_order"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long metricId=mapper.readTree(metricResponse).path("id").asLong();
        mvc.perform(post("/api/v1/admin/semantic/metrics/{id}/publish",metricId).param("domain","sales")
                        .header("Authorization",bearer(token))).andExpect(status().isOk());
        JsonNode metric=searchAsset(token,metricMarker,"METRIC");
        assertThat(metric.path("metadata").path("assetId").asText()).isEqualTo(String.valueOf(metricId));
        assertThat(metric.path("metadata").path("assetVersion").asText()).isNotBlank();
        assertThat(metric.path("metadata").path("status").asText()).isEqualTo("PUBLISHED");

        String questionMarker="probe_sql_question_973b";
        String sqlBodyMarker="sql_body_must_remain_in_oracle_973b";
        String exampleResponse=mvc.perform(post("/api/v1/admin/training/sql-examples")
                        .header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("domain","sales","question",questionMarker,
                                "sql","SELECT '"+sqlBodyMarker+"' AS marker FROM dual",
                                "explanation","unique approved question retrieval marker"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long exampleId=mapper.readTree(exampleResponse).path("id").asLong();
        mvc.perform(post("/api/v1/admin/training/sql-examples/{id}/publish",exampleId).param("domain","sales")
                        .header("Authorization",bearer(token))).andExpect(status().isOk());
        JsonNode example=searchAsset(token,questionMarker,"SQL_EXAMPLE");
        assertThat(example.path("metadata").path("assetId").asText()).isEqualTo(String.valueOf(exampleId));
        assertThat(example.path("content").asText()).doesNotContain(sqlBodyMarker);
    }

    private JsonNode searchAsset(String token,String query,String assetType) throws Exception {
        String response=mvc.perform(get("/api/v1/admin/knowledge/search").param("domain","sales")
                        .param("query",query).param("topK","20").header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for(JsonNode item:mapper.readTree(response))
            if(assetType.equals(item.path("metadata").path("assetType").asText())) return item;
        throw new AssertionError("Missing vector projection type "+assetType+" in "+response);
    }

    private String login() throws Exception {
        String response=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest("ai_admin","Aibi@123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("accessToken").asText();
    }

    private String bearer(String token){return "Bearer "+token;}
}
