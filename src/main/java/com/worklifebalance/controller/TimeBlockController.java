package com.worklifebalance.controller;

import com.worklifebalance.dto.TimeBlockDto;
import com.worklifebalance.service.TimeBlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/time-blocks")
@RequiredArgsConstructor
public class TimeBlockController {

    private final TimeBlockService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TimeBlockDto create(@Valid @RequestBody TimeBlockDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public TimeBlockDto update(@PathVariable Long id, @Valid @RequestBody TimeBlockDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
