package com.worklifebalance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DailyEntryDto {
    private Long id;

    @NotNull
    private LocalDate date;

    private Double workHours;
    private Double freeTimeHours;
    private Double sleepingHours;

    @NotNull
    @Min(1) @Max(10)
    private Integer mood;

    private String notes;

    private List<AppointmentDto> appointments;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
