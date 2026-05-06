package com.worklifebalance.repository;

import com.worklifebalance.model.Appointment;
import com.worklifebalance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    Optional<Appointment> findByIdAndDailyEntry_User(Long id, User user);
}
