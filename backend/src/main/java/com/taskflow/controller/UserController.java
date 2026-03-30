package com.taskflow.controller;

import com.taskflow.dto.RoleChangeRequest;
import com.taskflow.dto.UserDTO;
import com.taskflow.dto.UserProfileUpdateRequest;
import com.taskflow.exception.BadRequestException;
import com.taskflow.exception.ForbiddenException;
import com.taskflow.model.MemberType;
import com.taskflow.model.Role;
import com.taskflow.model.User;
import com.taskflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    // GET /api/users
    // Returns all users (id, username, email, role — no passwords).
    // Used for the assignment dropdown.
    @Autowired
    private com.taskflow.repository.TeamMemberRepository teamMemberRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userRepository.findAll().stream()
                .map(u -> {
                    List<String> teams = teamMemberRepository.findByUserId(u.getId()).stream()
                            .map(tm -> tm.getTeam().getName())
                            .toList();
                    return UserDTO.builder()
                            .id(u.getId())
                            .username(u.getUsername())
                            .email(u.getEmail())
                            .role(u.getRole().name())
                            .memberType(u.getMemberType() != null ? u.getMemberType().name() : null)
                            .teams(teams)
                            .build();
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    // GET /api/users/assignable
    // Returns only DEVELOPER/TESTER/DESIGNER users (for assignment dropdown).
    // Only ADMIN/MANAGER should call this (enforced on frontend).
    @GetMapping("/assignable")
    public ResponseEntity<List<UserDTO>> getAssignableUsers() {
        List<UserDTO> users = userRepository.findAll().stream()
                .filter(u -> u.getRole() != Role.VIEWER)
                .map(u -> UserDTO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .role(u.getRole().name())
                        .memberType(u.getMemberType() != null ? u.getMemberType().name() : null)
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    // PUT /api/users/{id}/role
    // Admin/Manager can change any user's role (no limit).
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MANAGER'))")
    @PutMapping("/{id}/role")
    public ResponseEntity<Map<String, String>> changeUserRole(
            @PathVariable Long id,
            @RequestBody RoleChangeRequest request) {
        User currentUser = getCurrentUser();

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
                "role", newRole.name()));
    }

    // PUT /api/users/me/memberType
    // Self change of memberType (DEVELOPER ↔ TESTER)
    // Enforced limit: 2 changes, 15-day cooldown.
    @PutMapping("/me/memberType")
    public ResponseEntity<Map<String, String>> changeMyMemberType(@RequestBody RoleChangeRequest request) {
        User currentUser = getCurrentUser();

        // cannot change if not MEMBER
        if (currentUser.getRole() != Role.MEMBER) {
            throw new ForbiddenException("Only members can change their specialization");
        }

        MemberType newType;
        try {
            newType = MemberType.valueOf(request.getRole().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid member type: " + request.getRole());
        }

        if (newType == currentUser.getMemberType()) {
            throw new BadRequestException("You already have this member type");
        }

        // Only DEVELOPER or TESTER allowed for self-change
        if (newType != MemberType.DEVELOPER && newType != MemberType.TESTER) {
            throw new ForbiddenException("You can only switch between DEVELOPER and TESTER");
        }

        // limit checks
        if (currentUser.getRoleChangeCount() >= 2) {
            if (currentUser.getLastRoleChangeAt() != null) {
                LocalDateTime cooldownEnd = currentUser.getLastRoleChangeAt().plusDays(15);
                if (LocalDateTime.now().isBefore(cooldownEnd)) {
                    throw new BadRequestException(
                            "Change limit reached. You can change again after " + cooldownEnd.toLocalDate());
                }
            }
            currentUser.setRoleChangeCount(0);
        }

        currentUser.setMemberType(newType);
        currentUser.setRoleChangeCount(currentUser.getRoleChangeCount() + 1);
        currentUser.setLastRoleChangeAt(LocalDateTime.now());
        userRepository.save(currentUser);

        return ResponseEntity.ok(Map.of(
                "message", "Member type changed to " + newType.name(),
                "memberType", newType.name(),
                "changesRemaining", String.valueOf(2 - currentUser.getRoleChangeCount())));
    }

    // GET /api/users/me
    // Returns current user's profile info.
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getMyProfile() {
        User user = getCurrentUser();
        return ResponseEntity.ok(UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .memberType(user.getMemberType() != null ? user.getMemberType().name() : null)
                .build());
    }

    // PUT /api/users/me
    // Update current user's profile (username/email).
    @PutMapping("/me")
    public ResponseEntity<UserDTO> updateMyProfile(@RequestBody UserProfileUpdateRequest request) {
        User current = getCurrentUser();

        if (request.getUsername() != null && !request.getUsername().isBlank()
                && !request.getUsername().equals(current.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new BadRequestException("Username already taken");
            }
            current.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && !request.getEmail().equals(current.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email already in use");
            }
            current.setEmail(request.getEmail());
        }

        userRepository.save(current);

        return ResponseEntity.ok(UserDTO.builder()
                .id(current.getId())
                .username(current.getUsername())
                .email(current.getEmail())
                .role(current.getRole().name())
                .memberType(current.getMemberType() != null ? current.getMemberType().name() : null)
                .build());
    }

    // PATCH /api/users/me/password
    // Allows authenticated user to change own password with current password
    // verification.
    @PatchMapping("/me/password")
    public ResponseEntity<Map<String, String>> changeMyPassword(
            @RequestBody com.taskflow.dto.UserPasswordChangeRequest request) {
        User current = getCurrentUser();

        if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            throw new BadRequestException("Current password is required");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isBlank()
                || request.getNewPassword().length() < 8) {
            throw new BadRequestException("New password must be at least 8 characters long");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), current.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), current.getPassword())) {
            throw new BadRequestException("New password must be different from current password");
        }

        current.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(current);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @Autowired
    private com.taskflow.service.UserSessionService userSessionService;

    @GetMapping("/me/sessions")
    public ResponseEntity<List<com.taskflow.dto.UserSessionDTO>> getMySessions(
            @RequestParam(required = false) String status) {
        User currentUser = getCurrentUser();
        String normStatus = status != null ? status.toUpperCase() : null;
        if (normStatus != null && !normStatus.equals("ACTIVE") && !normStatus.equals("INACTIVE")) {
            throw new BadRequestException("Invalid status filter");
        }
        List<com.taskflow.model.UserSession> sessions = userSessionService.getSessions(currentUser.getId(), normStatus);
        List<com.taskflow.dto.UserSessionDTO> sessionDTOs = sessions.stream()
                .map(s -> com.taskflow.dto.UserSessionDTO.builder()
                        .id(s.getId())
                        .jti(s.getJti())
                        .userAgent(s.getUserAgent())
                        .platform(s.getPlatform())
                        .createdAt(s.getCreatedAt())
                        .lastActive(s.getLastActive())
                        .status(s.getStatus())
                        .build())
                .toList();
        return ResponseEntity.ok(sessionDTOs);
    }

    @DeleteMapping("/me/sessions/{id}")
    public ResponseEntity<Void> revokeMySession(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        userSessionService.markRevoked(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Current user not found"));
    }
}
