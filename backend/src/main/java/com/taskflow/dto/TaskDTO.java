package com.taskflow.dto;

import com.taskflow.model.TaskPriority;
import com.taskflow.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDTO {

    private Long id;

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must be at most 100 characters")
    private String title;

    private String description;

    private TaskStatus status;

    private TaskPriority priority;

    private LocalDate dueDate;

    // F-EXT-02: Assignment
    private java.util.List<Long> assigneeIds;
    private java.util.List<String> assigneeNames;

    // F-W2-01: Task owner (used for permission checks)
    private Long userId;
    private String userName;

    // F-W2-01: Team assignment
    private Long teamId;
    private String teamName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // F-EXT-06: computed by backend for convenience
    private boolean overdue;
}
