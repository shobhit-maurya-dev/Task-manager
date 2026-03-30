package com.taskflow.controller;

import com.taskflow.dto.SubtaskDTO;
import com.taskflow.dto.SubtaskSummaryDTO;
import com.taskflow.service.SubtaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class SubtaskController {

    @Autowired
    private SubtaskService subtaskService;

    @PostMapping("/api/tasks/{taskId}/subtasks")
    public ResponseEntity<SubtaskDTO> createSubtask(
            @PathVariable Long taskId,
            @RequestBody Map<String, Object> payload) {
        String title = payload.getOrDefault("title", "").toString();
        Long assignedToId = payload.containsKey("assignedToId") ?
                (payload.get("assignedToId") instanceof Number ? ((Number) payload.get("assignedToId")).longValue() : null)
                : null;
        SubtaskDTO created = subtaskService.createSubtask(taskId, title, assignedToId);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/api/tasks/{taskId}/subtasks")
    public ResponseEntity<List<SubtaskDTO>> listSubtasks(@PathVariable Long taskId) {
        return ResponseEntity.ok(subtaskService.listSubtasks(taskId));
    }

    @GetMapping("/api/tasks/{taskId}/subtasks/summary")
    public ResponseEntity<SubtaskSummaryDTO> getSummary(@PathVariable Long taskId) {
        return ResponseEntity.ok(subtaskService.getSummary(taskId));
    }

    @PatchMapping("/api/subtasks/{id}/toggle")
    public ResponseEntity<SubtaskDTO> toggleSubtask(@PathVariable Long id) {
        return ResponseEntity.ok(subtaskService.toggleComplete(id));
    }

    @PutMapping("/api/subtasks/{id}")
    public ResponseEntity<SubtaskDTO> updateSubtask(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        String title = payload.getOrDefault("title", "").toString();
        Long assignedToId = payload.containsKey("assignedToId") ?
                (payload.get("assignedToId") instanceof Number ? ((Number) payload.get("assignedToId")).longValue() : null)
                : null;
        return ResponseEntity.ok(subtaskService.updateSubtask(id, title, assignedToId));
    }

    @GetMapping("/api/subtasks/{id}")
    public ResponseEntity<SubtaskDTO> getSubtask(@PathVariable Long id) {
        return ResponseEntity.ok(subtaskService.getById(id));
    }

    @DeleteMapping("/api/subtasks/{id}")
    public ResponseEntity<Void> deleteSubtask(@PathVariable Long id) {
        subtaskService.deleteSubtask(id);
        return ResponseEntity.noContent().build();
    }
}
