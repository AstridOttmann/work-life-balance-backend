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
class TimeBlockControllerTest {

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
    void createTimeBlock_withoutEndTime_returns201() throws Exception {
        String token = registerAndGetToken("tracker@example.com");

        var entry = new DailyEntryDto();
        entry.setDate(LocalDate.of(2026, 5, 5));
        entry.setMood(5.0);
        String entryBody = mockMvc.perform(post("/api/entries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry)))
                .andReturn().getResponse().getContentAsString();
        long entryId = objectMapper.readTree(entryBody).get("id").asLong();

        // Start: no endTime
        String startJson = String.format(
                "{\"dailyEntryId\":%d,\"type\":\"WORK\",\"startTime\":\"09:00:00\"}", entryId);

        String blockBody = mockMvc.perform(post("/api/time-blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endTime").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        long blockId = objectMapper.readTree(blockBody).get("id").asLong();

        // Stop: add endTime
        String stopJson = String.format(
                "{\"dailyEntryId\":%d,\"type\":\"WORK\",\"startTime\":\"09:00:00\",\"endTime\":\"10:30:00\"}", entryId);

        mockMvc.perform(put("/api/time-blocks/" + blockId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stopJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endTime").value("10:30:00"));
    }
}
