package com.orderaudit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String login(String user, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("username", user, "password", password));
        String resp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(resp).get("token").asText();
        assertNotNull(token);
        return "Bearer " + token;
    }
    // (kept local on purpose: this test class checks the login endpoint itself)

    @Test
    void loginWithCorrectPasswordReturnsTokenAndRole() throws Exception {
        String body = json.writeValueAsString(Map.of("username", "admin", "password", "Passw0rd!"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        String body = json.writeValueAsString(Map.of("username", "admin", "password", "nope"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankFieldsAre400() throws Exception {
        String body = json.writeValueAsString(Map.of("username", "", "password", ""));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIs401() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsLoggedInUser() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", login("owner", "Passw0rd!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void rolesAreEnforced() throws Exception {
        String salesman = login("salesman", "Passw0rd!");
        String admin = login("admin", "Passw0rd!");
        String accountant = login("accountant", "Passw0rd!");
        String supervisor = login("supervisor", "Passw0rd!");

        // salesman: no admin, no reports, no production
        mvc.perform(get("/api/reports/dashboard").header("Authorization", salesman)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rolecheck/admin").header("Authorization", salesman)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rolecheck/reports").header("Authorization", salesman)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rolecheck/production").header("Authorization", salesman)).andExpect(status().isForbidden());

        // admin: everything above
        mvc.perform(get("/api/rolecheck/admin").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/rolecheck/reports").header("Authorization", admin)).andExpect(status().isOk());

        // accountant: reports yes, admin no
        mvc.perform(get("/api/rolecheck/reports").header("Authorization", accountant)).andExpect(status().isOk());
        mvc.perform(get("/api/rolecheck/admin").header("Authorization", accountant)).andExpect(status().isForbidden());

        // supervisor: production yes, reports no
        mvc.perform(get("/api/rolecheck/production").header("Authorization", supervisor)).andExpect(status().isOk());
        mvc.perform(get("/api/rolecheck/reports").header("Authorization", supervisor)).andExpect(status().isForbidden());
    }

    @Test
    void allFiveDemoRolesCanLogIn() throws Exception {
        // sanity: one seeded demo login exists per business role
        for (String u : new String[]{"admin", "salesman", "supervisor", "accountant", "owner"}) {
            assertEquals(200, mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("username", u, "password", "Passw0rd!"))))
                    .andReturn().getResponse().getStatus());
        }
    }
}
