package com.worklifebalance.repository;

import com.worklifebalance.model.DailyEntry;
import com.worklifebalance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyEntryRepository extends JpaRepository<DailyEntry, Long> {
    List<DailyEntry> findByUserAndDateBetweenOrderByDateAsc(User user, LocalDate from, LocalDate to);
    List<DailyEntry> findAllByUserOrderByDateDesc(User user);
    Optional<DailyEntry> findByUserAndDate(User user, LocalDate date);
    Optional<DailyEntry> findByIdAndUser(Long id, User user);
}
