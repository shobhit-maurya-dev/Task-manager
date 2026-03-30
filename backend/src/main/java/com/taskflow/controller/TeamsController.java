package com.taskflow.controller;

import com.taskflow.dto.TaskDTO;
import com.taskflow.dto.TeamDTO;
import com.taskflow.dto.TeamMemberDTO;
import com.taskflow.exception.BadRequestException;
import com.taskflow.model.Team;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.service.TeamsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
public class TeamsController {

    @Autowired
    private TeamsService teamsService;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private TaskRepository taskRepository;

    @PostMapping
    public ResponseEntity<TeamDTO> createTeam(@RequestBody TeamDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BadRequestException("Team name is required");
        }
        Team t = teamsService.createTeam(dto.getName(), dto.getDescription());
        return ResponseEntity.status(201).body(toDTO(t));
    }

    @GetMapping
    public ResponseEntity<List<TeamDTO>> listTeams() {
        List<TeamDTO> list = teamsService.getTeamsForCurrentUser().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamDTO> getTeam(@PathVariable Long id) {
        Team t = teamsService.getTeamById(id);
        return ResponseEntity.ok(toDTO(t));
    }

    @PostMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> addMember(@PathVariable Long id, @PathVariable Long userId) {
        teamsService.addMember(id, userId);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        teamsService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/members/{userId}/leader")
    public ResponseEntity<Void> toggleLeader(@PathVariable Long id, @PathVariable Long userId, @RequestParam boolean isLeader) {
        teamsService.updateMemberLeadership(id, userId, isLeader);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long id) {
        teamsService.deleteTeam(id);
        return ResponseEntity.noContent().build();
    }

    private TeamDTO toDTO(Team t) {
        List<TeamMemberDTO> members = teamMemberRepository.findByTeamId(t.getId()).stream()
                .map(tm -> TeamMemberDTO.builder()
                        .id(tm.getUser().getId())
                        .username(tm.getUser().getUsername())
                        .email(tm.getUser().getEmail())
                        .role(tm.getUser().getRole().name())
                        .memberType(tm.getUser().getMemberType() != null ? tm.getUser().getMemberType().name() : "DEVELOPER")
                        .isLeader(tm.isLeader())
                        .joinedAt(tm.getJoinedAt())
                        .build())
                .collect(Collectors.toList());

        List<TaskDTO> tasks = taskRepository.findByTeamIdOrderByCreatedAtDesc(t.getId()).stream()
                .map(task -> TaskDTO.builder()
                        .id(task.getId())
                        .title(task.getTitle())
                        .description(task.getDescription())
                        .status(task.getStatus())
                        .priority(task.getPriority())
                        .dueDate(task.getDueDate())
                        .assigneeIds(task.getAssignees().stream().map(com.taskflow.model.User::getId).collect(Collectors.toList()))
                        .assigneeNames(task.getAssignees().stream().map(com.taskflow.model.User::getUsername).collect(Collectors.toList()))
                        .createdAt(task.getCreatedAt())
                        .updatedAt(task.getUpdatedAt())
                        .overdue(task.getDueDate() != null && task.getDueDate().isBefore(java.time.LocalDate.now()) && task.getStatus() != com.taskflow.model.TaskStatus.COMPLETED)
                        .build())
                .collect(Collectors.toList());

        return TeamDTO.builder()
                .id(t.getId())
                .name(t.getName())
                .description(t.getDescription())
                .managerId(t.getManager() != null ? t.getManager().getId() : null)
                .managerName(t.getManager() != null ? t.getManager().getUsername() : "Unknown")
                .createdAt(t.getCreatedAt())
                .members(members)
                .tasks(tasks)
                .build();
    }
}
