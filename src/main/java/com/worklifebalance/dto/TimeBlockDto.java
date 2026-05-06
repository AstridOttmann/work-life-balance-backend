package com.worklifebalance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeBlockDto {
    private Long id;

    @NotNull
    private Long dailyEntryId;

    @NotBlank
    private String type;

    @NotNull
    private LocalTime startTime;

    private LocalTime endTime;   // nullable — omitted from JSON when null

    private boolean paused;

    private long elapsedMs;

    private LocalTime segmentStartTime;  // nullable — omitted from JSON when null
}
