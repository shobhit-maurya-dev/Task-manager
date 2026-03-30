package com.taskflow.repository;

import com.taskflow.model.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    List<TimeEntry> findByTaskIdOrderByCreatedAtDesc(Long taskId);
    List<TimeEntry> findByUserIdOrderByCreatedAtDesc(Long userId);
}
