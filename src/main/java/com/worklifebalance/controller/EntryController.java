package com.worklifebalance.controller;

import com.worklifebalance.dto.DailyEntryDto;
import com.worklifebalance.dto.SummaryDto;
import com.worklifebalance.model.User;
import com.worklifebalance.service.EntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.getAll(user, from, to);
    }

    @GetMapping("/{id}")
    public DailyEntryDto getById(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return service.getById(user, id);
    }

    @GetMapping("/summary")
    public SummaryDto getSummary(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "weekly") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.getSummary(user, period, date != null ? date : LocalDate.now());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DailyEntryDto create(@AuthenticationPrincipal User user, @Valid @RequestBody DailyEntryDto dto) {
        return service.create(user, dto);
    }

    @PutMapping("/{id}")
    public DailyEntryDto update(@AuthenticationPrincipal User user,
                                @PathVariable Long id,
                                @Valid @RequestBody DailyEntryDto dto) {
        return service.update(user, id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user, id);
    }
}
