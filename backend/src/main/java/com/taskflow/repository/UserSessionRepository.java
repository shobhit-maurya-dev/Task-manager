package com.taskflow.repository;

import com.taskflow.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    List<UserSession> findByUserId(Long userId);
    List<UserSession> findByUserIdAndStatus(Long userId, String status);
    Optional<UserSession> findByIdAndUserId(Long id, Long userId);
    Optional<UserSession> findByJti(String jti);
}
