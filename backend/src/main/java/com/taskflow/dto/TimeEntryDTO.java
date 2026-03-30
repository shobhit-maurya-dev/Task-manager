package com.taskflow.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TimeEntryDTO {
    private Long id;
    private Long taskId;
    private Long userId;
    private String username;
    private Integer minutes;
    private java.time.LocalDate logDate;
    private Boolean isManual;
    private String description;
    private LocalDateTime createdAt;
}
