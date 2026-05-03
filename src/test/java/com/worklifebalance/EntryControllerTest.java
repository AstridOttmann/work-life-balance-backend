package com.worklifebalance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklifebalance.dto.DailyEntryDto;
import com.worklifebalance.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EntryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String registerAndGetToken(String email) throws Exception {
        var req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("password123");
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void getEntries_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/entries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getEntries_withToken_returnsOwnEntriesOnly() throws Exception {
        String aliceToken = registerAndGetToken("alice@example.com");
        String bobToken = registerAndGetToken("bob@example.com");

        var entry = new DailyEntryDto();
        entry.setDate(LocalDate.of(2026, 1, 1));
        entry.setMood(8.0);
        entry.setSleepingHours(7.5);

        mockMvc.perform(post("/api/entries")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entry)));

        mockMvc.perform(get("/api/entries")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/entries")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
