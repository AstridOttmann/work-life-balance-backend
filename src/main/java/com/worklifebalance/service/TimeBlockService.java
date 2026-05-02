package com.worklifebalance.service;

import com.worklifebalance.dto.TimeBlockDto;
import com.worklifebalance.model.DailyEntry;
import com.worklifebalance.model.TimeBlock;
import com.worklifebalance.repository.DailyEntryRepository;
import com.worklifebalance.repository.TimeBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TimeBlockService {

    private final TimeBlockRepository timeBlockRepository;
    private final DailyEntryRepository dailyEntryRepository;

    public TimeBlockDto create(TimeBlockDto dto) {
        DailyEntry entry = dailyEntryRepository.findById(dto.getDailyEntryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Daily entry not found"));
        return toDto(timeBlockRepository.save(toEntity(dto, entry)));
    }

    public TimeBlockDto update(Long id, TimeBlockDto dto) {
        TimeBlock block = findOrThrow(id);
        block.setType(dto.getType());
        block.setStartTime(dto.getStartTime());
        block.setEndTime(dto.getEndTime());
        return toDto(timeBlockRepository.save(block));
    }

    public void delete(Long id) {
        timeBlockRepository.delete(findOrThrow(id));
    }

    private TimeBlock findOrThrow(Long id) {
        return timeBlockRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Time block not found"));
    }

    private TimeBlock toEntity(TimeBlockDto dto, DailyEntry entry) {
        TimeBlock b = new TimeBlock();
        b.setDailyEntry(entry);
        b.setType(dto.getType());
        b.setStartTime(dto.getStartTime());
        b.setEndTime(dto.getEndTime());
        return b;
    }

    TimeBlockDto toDto(TimeBlock b) {
        TimeBlockDto dto = new TimeBlockDto();
        dto.setId(b.getId());
        dto.setDailyEntryId(b.getDailyEntry().getId());
        dto.setType(b.getType());
        dto.setStartTime(b.getStartTime());
        dto.setEndTime(b.getEndTime());
        return dto;
    }
}
