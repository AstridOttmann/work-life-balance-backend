package com.worklifebalance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class AppointmentDto {
    private Long id;

    @NotNull
    private Long dailyEntryId;

    @NotBlank
    private String title;

    private LocalTime time;

    private Double durationHours;
}
