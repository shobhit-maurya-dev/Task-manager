package com.taskflow.repository;

import com.taskflow.model.Task;
import com.taskflow.model.TaskPriority;
import com.taskflow.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Task> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, TaskStatus status);

    Optional<Task> findByIdAndUserId(Long id, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);

    // ─── Expansion queries (Second Phase Expansion )

    List<Task> findByUserIdAndPriorityOrderByCreatedAtDesc(Long userId, TaskPriority priority);

    List<Task> findByUserIdAndStatusAndPriorityOrderByCreatedAtDesc(
            Long userId, TaskStatus status, TaskPriority priority);

    List<Task> findByAssigneesIdOrderByCreatedAtDesc(Long userId);

    // Tasks associated with a team
    List<Task> findByTeamIdOrderByCreatedAtDesc(Long teamId);

    // Tasks for teams - used for team visibility
    List<Task> findByTeamIdInOrderByCreatedAtDesc(List<Long> teamIds);

    // Analytics: count by status
    long countByUserIdAndStatus(Long userId, TaskStatus status);

    // Analytics: count by priority
    long countByUserIdAndPriority(Long userId, TaskPriority priority);

    // Analytics: overdue (due before today, not completed)
    List<Task> findByUserIdAndDueDateBeforeAndStatusNot(Long userId, LocalDate date, TaskStatus status);
}
