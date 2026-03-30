package com.taskflow.controller;

import com.taskflow.dto.ActivityLogDTO;
import com.taskflow.service.ActivityLogService;
import com.taskflow.model.User;
import com.taskflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/activity")
public class ActivityFeedController {

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<ActivityLogDTO>> getMyFeed(@RequestParam(defaultValue = "false") boolean excludeMe) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(activityLogService.getFeedForCurrentUser(excludeMe));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        String email = auth.getName();
        Optional<User> user = userRepository.findByEmail(email);
        return user.map(User::getId).orElse(null);
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<ActivityLogDTO>> getTaskFeed(@PathVariable Long taskId) {
        return ResponseEntity.ok(activityLogService.getFeedByTask(taskId));
    }
}