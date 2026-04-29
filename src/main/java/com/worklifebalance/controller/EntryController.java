package com.worklifebalance.controller;

import com.worklifebalance.dto.DailyEntryDto;
import com.worklifebalance.dto.SummaryDto;
import com.worklifebalance.service.EntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/entries")
@RequiredArgsConstructor
public class EntryController {

    private final EntryService service;

    @GetMapping
    public List<DailyEntryDto> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.getAll(from, to);
    }

    @GetMapping("/{id}")
    public DailyEntryDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/summary")
    public SummaryDto getSummary(
            @RequestParam(defaultValue = "weekly") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.getSummary(period, date != null ? date : LocalDate.now());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DailyEntryDto create(@Valid @RequestBody DailyEntryDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public DailyEntryDto update(@PathVariable Long id, @Valid @RequestBody DailyEntryDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
