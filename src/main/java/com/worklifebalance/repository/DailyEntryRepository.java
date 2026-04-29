package com.worklifebalance.repository;

import com.worklifebalance.model.DailyEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyEntryRepository extends JpaRepository<DailyEntry, Long> {
    List<DailyEntry> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
    Optional<DailyEntry> findByDate(LocalDate date);
}
