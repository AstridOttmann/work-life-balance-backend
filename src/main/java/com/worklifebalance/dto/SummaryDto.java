package com.worklifebalance.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SummaryDto {
    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
    private double totalWorkHours;
    private double totalFreeTimeHours;
    private double totalSleepingHours;
    private double totalAppointmentHours;
    private int appointmentCount;
    private double avgMood;
    private List<DailyEntryDto> entries;
}
