package com.taskflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubtaskDTO {
    private Long id;
    private String title;
    private boolean isComplete;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Long assignedToId;
    private String assignedToName;
    private Long createdById;
    private String createdByName;
}
