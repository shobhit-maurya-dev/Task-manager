package com.taskflow.repository;

import com.taskflow.model.ActiveTimer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActiveTimerRepository extends JpaRepository<ActiveTimer, Long> {
    Optional<ActiveTimer> findByTaskIdAndUserId(Long taskId, Long userId);

    List<ActiveTimer> findByTaskId(Long taskId);
}
