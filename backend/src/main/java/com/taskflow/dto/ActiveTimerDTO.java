package com.taskflow.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ActiveTimerDTO {
    private Long id;
    private Long taskId;
    private Long userId;
    private String username;
    private LocalDateTime startTime;
    private Long elapsedSeconds;
}
