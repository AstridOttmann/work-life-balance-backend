package com.worklifebalance.controller;

import com.worklifebalance.dto.TimeBlockDto;
import com.worklifebalance.model.User;
import com.worklifebalance.service.TimeBlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/time-blocks")
@RequiredArgsConstructor
public class TimeBlockController {

    private final TimeBlockService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TimeBlockDto create(@AuthenticationPrincipal User user, @Valid @RequestBody TimeBlockDto dto) {
        return service.create(user, dto);
    }

    @PutMapping("/{id}")
    public TimeBlockDto update(@AuthenticationPrincipal User user,
                               @PathVariable Long id,
                               @Valid @RequestBody TimeBlockDto dto) {
        return service.update(user, id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user, id);
    }
}
