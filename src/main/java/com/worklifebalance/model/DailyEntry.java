package com.worklifebalance.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "daily_entry",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "date"})
)
@Getter
@Setter
public class DailyEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    private Double workHours;
    private Double freeTimeHours;
    private Double sleepingHours;

    @Column(nullable = false)
    private Double mood;

    private Double health;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "dailyEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Appointment> appointments = new ArrayList<>();

    @OneToMany(mappedBy = "dailyEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimeBlock> timeBlocks = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
