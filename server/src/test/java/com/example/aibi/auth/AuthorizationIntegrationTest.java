package com.example.aibi.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/query-runs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void questionUserCanQueryButCannotUseReportsOrTraining() throws Exception {
        String token = login("question_user");
        mvc.perform(get("/api/v1/query-runs").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/training/dashboard").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reporterAndAdminReceiveTheirConfiguredPermissions() throws Exception {
        String reportToken = login("report_user");
        mvc.perform(get("/api/v1/reports").header("Authorization", bearer(reportToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/training/dashboard").header("Authorization", bearer(reportToken)))
                .andExpect(status().isForbidden());

        String adminToken = login("ai_admin");
        mvc.perform(get("/api/v1/admin/training/dashboard").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheToken() throws Exception {
        String token = login("question_user");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/query-runs").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(username, "Aibi@123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(response);
        return json.get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
