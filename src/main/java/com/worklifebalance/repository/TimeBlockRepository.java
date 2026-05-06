package com.worklifebalance.repository;

import com.worklifebalance.model.TimeBlock;
import com.worklifebalance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TimeBlockRepository extends JpaRepository<TimeBlock, Long> {
    Optional<TimeBlock> findByIdAndDailyEntry_User(Long id, User user);
}
