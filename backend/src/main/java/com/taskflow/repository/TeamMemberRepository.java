package com.taskflow.repository;

import com.taskflow.model.TeamMember;
import com.taskflow.model.TeamMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {
    List<TeamMember> findByTeamId(Long teamId);
    List<TeamMember> findByUserId(Long userId);
    void deleteByTeamIdAndUserId(Long teamId, Long userId);
}