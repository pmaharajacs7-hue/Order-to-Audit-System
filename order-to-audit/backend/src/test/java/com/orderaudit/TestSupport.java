package com.orderaudit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared helpers for integration tests. */
final class TestSupport {

    private TestSupport() {}

    /** Logs in as a seeded demo user and returns the "Bearer ..." header value. */
    static String bearer(MockMvc mvc, ObjectMapper json, String username) throws Exception {
        String body = json.writeValueAsString(Map.of("username", username, "password", "Passw0rd!"));
        String resp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(resp).get("token").asText();
    }
}
