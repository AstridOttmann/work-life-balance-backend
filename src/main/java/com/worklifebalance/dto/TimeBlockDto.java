package com.worklifebalance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class TimeBlockDto {
    private Long id;

    @NotNull
    private Long dailyEntryId;

    @NotBlank
    private String type;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;
}
