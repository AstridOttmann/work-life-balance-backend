package com.worklifebalance.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "appointment")
@Getter
@Setter
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "daily_entry_id")
    private DailyEntry dailyEntry;

    @Column(nullable = false)
    private String title;

    private LocalTime time;

    private Double durationHours;
}
