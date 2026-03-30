package com.taskflow.controller;

import com.taskflow.dto.AdminUserDTO;
import com.taskflow.dto.RoleChangeRequest;
import com.taskflow.dto.StatusChangeRequest;
import com.taskflow.exception.BadRequestException;
import com.taskflow.model.Role;
import com.taskflow.model.User;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.TeamRepository;
import com.taskflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private TeamRepository teamRepository;

    // GET /api/admin/users
    @GetMapping("/users")
    public List<AdminUserDTO> listUsers() {
        return userRepository.findAll().stream()
                .map(u -> {
                    try {
                        return toAdminUserDTO(u);
                    } catch (Exception e) {
                        // Log the error for the specific user and continue
                        System.err.println("Error mapping user " + u.getId() + ": " + e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    // PATCH /api/admin/users/{id}/role
    @PatchMapping("/users/{id}/role")
    public ResponseEntity<Map<String, String>> changeUserRole(
            @PathVariable Long id,
            @RequestBody RoleChangeRequest request) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User not found with id: " + id));

        Role newRole;
        try {
            newRole = Role.valueOf(request.getRole().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid role: " + request.getRole());
        }

        if (targetUser.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
            throw new BadRequestException("Cannot change the role of an Admin");
        }

        if (!targetUser.getRole().equals(newRole)) {
            targetUser.setRole(newRole);
            targetUser.setRoleChangeCount(targetUser.getRoleChangeCount() + 1);
            targetUser.setLastRoleChangeAt(java.time.LocalDateTime.now());
            userRepository.save(targetUser);
        }

        return ResponseEntity.ok(Map.of(
                "message", targetUser.getUsername() + "'s role changed to " + newRole.name(),
                "role", newRole.name()
        ));
    }

    // DELETE /api/admin/users/{id}
    // Soft-delete user to avoid integrity constraint issues with activity logs/tasks.
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User not found with id: " + id));

        if (targetUser.getRole() == Role.ADMIN) {
            throw new BadRequestException("Cannot delete Admin user");
        }

        targetUser.setActive(false);
        userRepository.save(targetUser);

        return ResponseEntity.ok(Map.of(
                "message", "User " + targetUser.getUsername() + " deactivated",
                "active", false
        ));
    }

    // PATCH /api/admin/users/{id}/status
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<Map<String, Object>> changeUserStatus(
            @PathVariable Long id,
            @RequestBody StatusChangeRequest request) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User not found with id: " + id));

        if (targetUser.getRole() == Role.ADMIN && !request.isActive()) {
            throw new BadRequestException("Cannot deactivate the Admin account");
        }

        targetUser.setActive(request.isActive());
        userRepository.save(targetUser);

        return ResponseEntity.ok(Map.of(
                "message", "User " + targetUser.getUsername() + " is now " + (request.isActive() ? "active" : "inactive"),
                "active", request.isActive()
        ));
    }

    private AdminUserDTO toAdminUserDTO(User user) {
        List<Long> teamIds = teamMemberRepository.findByUserId(user.getId()).stream()
                .filter(tm -> tm.getTeam() != null)
                .map(tm -> tm.getTeam().getId())
                .collect(Collectors.toList());

        List<String> teamNames = teamIds.isEmpty() ? List.of() :
            teamRepository.findAllById(teamIds).stream()
                .filter(java.util.Objects::nonNull)
                .map(com.taskflow.model.Team::getName)
                .collect(Collectors.toList());

        return AdminUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .teamIds(teamIds)
                .teamNames(teamNames)
                .build();
    }
}
