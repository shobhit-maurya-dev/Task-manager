package com.taskflow.controller;

import com.taskflow.dto.RoleChangeRequest;
import com.taskflow.dto.UserDTO;
import com.taskflow.exception.BadRequestException;
import com.taskflow.exception.ForbiddenException;
import com.taskflow.model.Role;
import com.taskflow.model.User;
import com.taskflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    //  GET /api/users
    //  Returns all users (id, username, email, role — no passwords).
    //  Used for the assignment dropdown.
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userRepository.findAll().stream()
                .map(u -> UserDTO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .role(u.getRole().name())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    //  GET /api/users/assignable
    //  Returns only DEVELOPER + TESTER users (for assignment dropdown).
    //  Only ADMIN/MANAGER should call this (enforced on frontend).
    @GetMapping("/assignable")
    public ResponseEntity<List<UserDTO>> getAssignableUsers() {
        List<UserDTO> users = userRepository
                .findByRoleIn(Arrays.asList(Role.DEVELOPER, Role.TESTER))
                .stream()
                .map(u -> UserDTO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .role(u.getRole().name())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    //  PUT /api/users/me/role
    //  Self role change: max 2 times, then 15-day cooldown.
    //  Only DEVELOPER ↔ TESTER allowed.
    @PutMapping("/me/role")
    public ResponseEntity<Map<String, String>> changeMyRole(@RequestBody RoleChangeRequest request) {
        User currentUser = getCurrentUser();

        // Admin/Manager cannot self-change via this endpoint
        if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER) {
            throw new ForbiddenException("Admin/Manager cannot change their own role this way");
        }

        Role newRole;
        try {
            newRole = Role.valueOf(request.getRole().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid role: " + request.getRole());
        }

        // Only DEVELOPER or TESTER allowed for self
        if (newRole != Role.DEVELOPER && newRole != Role.TESTER) {
            throw new ForbiddenException("You can only switch between DEVELOPER and TESTER");
        }

        if (newRole == currentUser.getRole()) {
            throw new BadRequestException("You already have this role");
        }

        // Check 2-time limit and 15-day cooldown
        if (currentUser.getRoleChangeCount() >= 2) {
            if (currentUser.getLastRoleChangeAt() != null) {
                LocalDateTime cooldownEnd = currentUser.getLastRoleChangeAt().plusDays(15);
                if (LocalDateTime.now().isBefore(cooldownEnd)) {
                    throw new BadRequestException(
                            "Role change limit reached. You can change again after " + cooldownEnd.toLocalDate());
                }
            }
            // Reset count after cooldown
            currentUser.setRoleChangeCount(0);
        }

        currentUser.setRole(newRole);
        currentUser.setRoleChangeCount(currentUser.getRoleChangeCount() + 1);
        currentUser.setLastRoleChangeAt(LocalDateTime.now());
        userRepository.save(currentUser);

        return ResponseEntity.ok(Map.of(
                "message", "Role changed to " + newRole.name(),
                "role", newRole.name(),
                "changesRemaining", String.valueOf(2 - currentUser.getRoleChangeCount())
        ));
    }

    //  PUT /api/users/{id}/role
    //  Admin/Manager can change any user's role (no limit).
    @PutMapping("/{id}/role")
    public ResponseEntity<Map<String, String>> changeUserRole(
            @PathVariable Long id,
            @RequestBody RoleChangeRequest request) {
        User currentUser = getCurrentUser();

        // Only Admin/Manager can do this
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            throw new ForbiddenException("Only Admin or Manager can change other users' roles");
        }

        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User not found with id: " + id));

        Role newRole;
        try {
            newRole = Role.valueOf(request.getRole().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid role: " + request.getRole());
        }

        // Cannot change another Admin (only Admin can promote to Admin)
        if (targetUser.getRole() == Role.ADMIN && currentUser.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only Admin can change another Admin's role");
        }

        // Only Admin can promote to ADMIN
        if (newRole == Role.ADMIN && currentUser.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only Admin can promote to Admin role");
        }

        targetUser.setRole(newRole);
        userRepository.save(targetUser);

        return ResponseEntity.ok(Map.of(
                "message", targetUser.getUsername() + "'s role changed to " + newRole.name(),
                "role", newRole.name()
        ));
    }

    //  GET /api/users/me
    //  Returns current user's profile info.
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getMyProfile() {
        User user = getCurrentUser();
        return ResponseEntity.ok(UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build());
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Current user not found"));
    }
}
