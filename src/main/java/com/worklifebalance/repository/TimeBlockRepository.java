package com.worklifebalance.repository;

import com.worklifebalance.model.TimeBlock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeBlockRepository extends JpaRepository<TimeBlock, Long> {
}
