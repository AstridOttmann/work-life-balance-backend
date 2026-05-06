package com.worklifebalance.service;

import com.worklifebalance.dto.AppointmentDto;
import com.worklifebalance.dto.DailyEntryDto;
import com.worklifebalance.dto.SummaryDto;
import com.worklifebalance.dto.TimeBlockDto;
import com.worklifebalance.model.DailyEntry;
import com.worklifebalance.model.TimeBlock;
import com.worklifebalance.model.User;
import com.worklifebalance.repository.DailyEntryRepository;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EntryService {

    private final DailyEntryRepository repository;

    public List<DailyEntryDto> getAll(User user, LocalDate from, LocalDate to) {
        List<DailyEntry> entries = (from != null && to != null)
                ? repository.findByUserAndDateBetweenOrderByDateAsc(user, from, to)
                : repository.findAllByUserOrderByDateDesc(user);
        return entries.stream().map(this::toDto).toList();
    }

    public DailyEntryDto getById(User user, Long id) {
        return toDto(findOrThrow(user, id));
    }

    public DailyEntryDto create(User user, DailyEntryDto dto) {
        if (repository.findByUserAndDate(user, dto.getDate()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An entry for this date already exists");
        }
        DailyEntry entry = toEntity(dto);
        entry.setUser(user);
        return toDto(repository.save(entry));
    }

    public DailyEntryDto update(User user, Long id, DailyEntryDto dto) {
        DailyEntry entry = findOrThrow(user, id);
        repository.findByUserAndDate(user, dto.getDate()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An entry for this date already exists");
            }
        });
        entry.setDate(dto.getDate());
        entry.setSleepingHours(dto.getSleepingHours());
        entry.setMood(dto.getMood());
        entry.setHealth(dto.getHealth());
        entry.setNotes(dto.getNotes());
        return toDto(repository.save(entry));
    }

    public void delete(User user, Long id) {
        repository.delete(findOrThrow(user, id));
    }

    public SummaryDto getSummary(User user, String period, LocalDate date) {
        LocalDate start;
        LocalDate end;
        if ("monthly".equalsIgnoreCase(period)) {
            start = date.with(TemporalAdjusters.firstDayOfMonth());
            end = date.with(TemporalAdjusters.lastDayOfMonth());
        } else {
            start = date.with(DayOfWeek.MONDAY);
            end = date.with(DayOfWeek.SUNDAY);
        }

        List<DailyEntry> entries = repository.findByUserAndDateBetweenOrderByDateAsc(user, start, end);

        SummaryDto summary = new SummaryDto();
        summary.setPeriod(period);
        summary.setStartDate(start);
        summary.setEndDate(end);
        summary.setEntries(entries.stream().map(this::toDto).toList());

        summary.setTotalWorkHours(entries.stream().mapToDouble(e ->
                e.getTimeBlocks().isEmpty() ? orZero(e.getWorkHours())
                : e.getTimeBlocks().stream()
                        .filter(b -> "WORK".equals(b.getType()) && b.getEndTime() != null)
                        .mapToDouble(b -> minutesBetween(b) / 60.0).sum()
        ).sum());
        summary.setTotalFreeTimeHours(entries.stream().mapToDouble(e ->
                e.getTimeBlocks().isEmpty() ? orZero(e.getFreeTimeHours())
                : e.getTimeBlocks().stream()
                        .filter(b -> "FREE".equals(b.getType()) && b.getEndTime() != null)
                        .mapToDouble(b -> minutesBetween(b) / 60.0).sum()
        ).sum());
        summary.setTotalSleepingHours(entries.stream()
                .mapToDouble(e -> orZero(e.getSleepingHours())).sum());

        double totalAppHours = entries.stream()
                .flatMap(e -> e.getAppointments().stream())
                .mapToDouble(a -> orZero(a.getDurationHours()))
                .sum();
        summary.setTotalAppointmentHours(totalAppHours);
        summary.setAppointmentCount(entries.stream()
                .mapToInt(e -> e.getAppointments().size()).sum());

        summary.setAvgMood(entries.stream()
                .filter(e -> e.getMood() != null)
                .mapToDouble(DailyEntry::getMood)
                .average().orElse(0));

        summary.setAvgHealth(entries.stream()
                .filter(e -> e.getHealth() != null)
                .mapToDouble(DailyEntry::getHealth)
                .average().orElse(0));

        return summary;
    }

    private DailyEntry findOrThrow(User user, Long id) {
        return repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));
    }

    private double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    DailyEntryDto toDto(DailyEntry entry) {
        DailyEntryDto dto = new DailyEntryDto();
        dto.setId(entry.getId());
        dto.setDate(entry.getDate());
        boolean hasBlocks = !entry.getTimeBlocks().isEmpty();
        double compWork = entry.getTimeBlocks().stream()
                .filter(b -> "WORK".equals(b.getType()) && b.getEndTime() != null)
                .mapToDouble(b -> minutesBetween(b) / 60.0).sum();
        double compFree = entry.getTimeBlocks().stream()
                .filter(b -> "FREE".equals(b.getType()) && b.getEndTime() != null)
                .mapToDouble(b -> minutesBetween(b) / 60.0).sum();
        dto.setWorkHours(hasBlocks ? (compWork > 0 ? compWork : null) : entry.getWorkHours());
        dto.setFreeTimeHours(hasBlocks ? (compFree > 0 ? compFree : null) : entry.getFreeTimeHours());
        dto.setSleepingHours(entry.getSleepingHours());
        dto.setMood(entry.getMood());
        dto.setHealth(entry.getHealth());
        dto.setNotes(entry.getNotes());
        dto.setCreatedAt(entry.getCreatedAt());
        dto.setUpdatedAt(entry.getUpdatedAt());
        dto.setTimeBlocks(entry.getTimeBlocks().stream().map(b -> {
            TimeBlockDto tb = new TimeBlockDto();
            tb.setId(b.getId());
            tb.setDailyEntryId(entry.getId());
            tb.setType(b.getType());
            tb.setStartTime(b.getStartTime());
            tb.setEndTime(b.getEndTime());
            tb.setPaused(b.isPaused());
            tb.setElapsedMs(b.getElapsedMs());
            tb.setSegmentStartTime(b.getSegmentStartTime());
            return tb;
        }).toList());
        dto.setAppointments(entry.getAppointments().stream().map(a -> {
            AppointmentDto ad = new AppointmentDto();
            ad.setId(a.getId());
            ad.setDailyEntryId(entry.getId());
            ad.setTitle(a.getTitle());
            ad.setTime(a.getTime());
            ad.setDurationHours(a.getDurationHours());
            return ad;
        }).toList());
        return dto;
    }

    private DailyEntry toEntity(DailyEntryDto dto) {
        DailyEntry entry = new DailyEntry();
        entry.setDate(dto.getDate());
        entry.setSleepingHours(dto.getSleepingHours());
        entry.setMood(dto.getMood());
        entry.setHealth(dto.getHealth());
        entry.setNotes(dto.getNotes());
        return entry;
    }

    private double minutesBetween(TimeBlock b) {
        long mins = ChronoUnit.MINUTES.between(b.getStartTime(), b.getEndTime());
        return mins < 0 ? mins + 1440 : mins;
    }
}
