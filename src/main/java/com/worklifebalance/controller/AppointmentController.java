package com.worklifebalance.controller;

import com.worklifebalance.dto.AppointmentDto;
import com.worklifebalance.model.User;
import com.worklifebalance.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentDto create(@AuthenticationPrincipal User user, @Valid @RequestBody AppointmentDto dto) {
        return service.create(user, dto);
    }

    @PutMapping("/{id}")
    public AppointmentDto update(@AuthenticationPrincipal User user,
                                 @PathVariable Long id,
                                 @Valid @RequestBody AppointmentDto dto) {
        return service.update(user, id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user, id);
    }
}
