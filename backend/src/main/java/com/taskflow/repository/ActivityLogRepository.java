package com.taskflow.repository;

import com.taskflow.model.ActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByActorIdOrderByCreatedAtDesc(Long actorId, Pageable pageable);

    List<ActivityLog> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    void deleteByTaskId(Long taskId);

    @Query("SELECT a FROM ActivityLog a JOIN a.task t JOIN t.assignees u " +
           "WHERE u.id = :userId AND a.actor.id != :userId " +
           "ORDER BY a.createdAt DESC")
    List<ActivityLog> findByTaskAssigneeId(@Param("userId") Long userId, Pageable pageable);

    /**
     * More comprehensive notification query: 
     * Includes actions on tasks where user is assignee OR creator, 
     * but ALWAYS excludes actions by the user themselves.
     */
    @Query("SELECT DISTINCT a FROM ActivityLog a " +
           "LEFT JOIN a.task t " +
           "LEFT JOIN t.assignees u " +
           "WHERE (u.id = :userId OR t.user.id = :userId) " +
           "AND a.actor.id != :userId " +
           "ORDER BY a.createdAt DESC")
    List<ActivityLog> findNotificationsForUser(@Param("userId") Long userId, Pageable pageable);
}
