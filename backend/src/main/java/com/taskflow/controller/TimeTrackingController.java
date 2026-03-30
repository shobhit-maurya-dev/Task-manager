package com.taskflow.controller;

import com.taskflow.dto.ActiveTimerDTO;
import com.taskflow.dto.TimeEntryDTO;
import com.taskflow.dto.TimeEntryRequest;
import com.taskflow.service.TimeEntryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TimeTrackingController {

    @Autowired
    private TimeEntryService timeEntryService;

    @PostMapping("/tasks/{taskId}/timer/start")
    public ResponseEntity<TimeEntryDTO> startTimer(@PathVariable Long taskId) {
        return ResponseEntity.ok(timeEntryService.startTimer(taskId));
    }

    @PostMapping("/tasks/{taskId}/timer/stop")
    public ResponseEntity<TimeEntryDTO> stopTimer(@PathVariable Long taskId) {
        return ResponseEntity.ok(timeEntryService.stopTimer(taskId));
    }

    @GetMapping("/tasks/{taskId}/timer")
    public ResponseEntity<ActiveTimerDTO> getActiveTimer(@PathVariable Long taskId) {
        ActiveTimerDTO active = timeEntryService.getActiveTimer(taskId);
        if (active == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(active);
    }

    @PostMapping("/tasks/{taskId}/time-logs")
    public ResponseEntity<TimeEntryDTO> logTimeManual(
            @PathVariable Long taskId,
            @Valid @RequestBody TimeEntryRequest request) {
        return new ResponseEntity<>(timeEntryService.logTime(taskId, request), HttpStatus.CREATED);
    }

    @GetMapping("/tasks/{taskId}/time-logs")
    public ResponseEntity<List<TimeEntryDTO>> listTimeLogs(@PathVariable Long taskId) {
        return ResponseEntity.ok(timeEntryService.listByTask(taskId));
    }

    @GetMapping("/tasks/{taskId}/time-logs/total")
    public ResponseEntity<Map<String, Integer>> getTotalTime(@PathVariable Long taskId) {
        int totalMinutes = timeEntryService.getTotalMinutes(taskId);
        Map<String, Integer> result = new HashMap<>();
        result.put("totalMinutes", totalMinutes);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/time-logs/{id}")
    public ResponseEntity<Void> deleteTimeLog(@PathVariable Long id) {
        timeEntryService.deleteTimeEntry(id);
        return ResponseEntity.noContent().build();
    }
}
