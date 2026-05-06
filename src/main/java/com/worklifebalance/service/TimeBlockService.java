package com.worklifebalance.service;

import com.worklifebalance.dto.TimeBlockDto;
import com.worklifebalance.model.DailyEntry;
import com.worklifebalance.model.TimeBlock;
import com.worklifebalance.model.User;
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

    public TimeBlockDto create(User user, TimeBlockDto dto) {
        DailyEntry entry = dailyEntryRepository.findByIdAndUser(dto.getDailyEntryId(), user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Daily entry not found"));
        return toDto(timeBlockRepository.save(toEntity(dto, entry)));
    }

    public TimeBlockDto update(User user, Long id, TimeBlockDto dto) {
        TimeBlock block = findOrThrow(user, id);
        block.setType(dto.getType());
        block.setStartTime(dto.getStartTime());
        block.setEndTime(dto.getEndTime());
        block.setPaused(dto.isPaused());
        block.setElapsedMs(dto.getElapsedMs());
        block.setSegmentStartTime(dto.getSegmentStartTime());
        return toDto(timeBlockRepository.save(block));
    }

    public void delete(User user, Long id) {
        timeBlockRepository.delete(findOrThrow(user, id));
    }

    private TimeBlock findOrThrow(User user, Long id) {
        return timeBlockRepository.findByIdAndDailyEntry_User(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Time block not found"));
    }

    private TimeBlock toEntity(TimeBlockDto dto, DailyEntry entry) {
        TimeBlock b = new TimeBlock();
        b.setDailyEntry(entry);
        b.setType(dto.getType());
        b.setStartTime(dto.getStartTime());
        b.setEndTime(dto.getEndTime());
        b.setPaused(dto.isPaused());
        b.setElapsedMs(dto.getElapsedMs());
        b.setSegmentStartTime(dto.getSegmentStartTime());
        return b;
    }

    TimeBlockDto toDto(TimeBlock b) {
        TimeBlockDto dto = new TimeBlockDto();
        dto.setId(b.getId());
        dto.setDailyEntryId(b.getDailyEntry().getId());
        dto.setType(b.getType());
        dto.setStartTime(b.getStartTime());
        dto.setEndTime(b.getEndTime());
        dto.setPaused(b.isPaused());
        dto.setElapsedMs(b.getElapsedMs());
        dto.setSegmentStartTime(b.getSegmentStartTime());
        return dto;
    }
}
