package com.worklifebalance.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "time_block")
@Getter
@Setter
public class TimeBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "daily_entry_id")
    private DailyEntry dailyEntry;

    @Column(nullable = false)
    private String type; // "WORK" or "FREE"

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = true)
    private LocalTime endTime;

    @Column(nullable = false)
    private boolean paused = false;
}
