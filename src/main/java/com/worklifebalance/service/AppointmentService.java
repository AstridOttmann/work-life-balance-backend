package com.worklifebalance.service;

import com.worklifebalance.dto.AppointmentDto;
import com.worklifebalance.model.Appointment;
import com.worklifebalance.model.DailyEntry;
import com.worklifebalance.repository.AppointmentRepository;
import com.worklifebalance.repository.DailyEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DailyEntryRepository dailyEntryRepository;

    public AppointmentDto create(AppointmentDto dto) {
        DailyEntry entry = dailyEntryRepository.findById(dto.getDailyEntryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Daily entry not found"));
        Appointment appointment = toEntity(dto, entry);
        return toDto(appointmentRepository.save(appointment));
    }

    public AppointmentDto update(Long id, AppointmentDto dto) {
        Appointment appointment = findOrThrow(id);
        appointment.setTitle(dto.getTitle());
        appointment.setTime(dto.getTime());
        appointment.setDurationHours(dto.getDurationHours());
        return toDto(appointmentRepository.save(appointment));
    }

    public void delete(Long id) {
        appointmentRepository.delete(findOrThrow(id));
    }

    private Appointment findOrThrow(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
    }

    private Appointment toEntity(AppointmentDto dto, DailyEntry entry) {
        Appointment a = new Appointment();
        a.setDailyEntry(entry);
        a.setTitle(dto.getTitle());
        a.setTime(dto.getTime());
        a.setDurationHours(dto.getDurationHours());
        return a;
    }

    AppointmentDto toDto(Appointment a) {
        AppointmentDto dto = new AppointmentDto();
        dto.setId(a.getId());
        dto.setDailyEntryId(a.getDailyEntry().getId());
        dto.setTitle(a.getTitle());
        dto.setTime(a.getTime());
        dto.setDurationHours(a.getDurationHours());
        return dto;
    }
}
