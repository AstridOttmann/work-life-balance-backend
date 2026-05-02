package com.worklifebalance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
    @DecimalMin("1.0") @DecimalMax("10.0")
    private Double mood;

    @DecimalMin("1.0") @DecimalMax("10.0")
    private Double health;

    private String notes;

    private List<AppointmentDto> appointments;
    private List<TimeBlockDto> timeBlocks;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
