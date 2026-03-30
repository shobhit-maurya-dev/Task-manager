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
public class UserSessionDTO {
    private Long id;
    private String jti;
    private String userAgent;
    private String platform;
    private LocalDateTime createdAt;
    private LocalDateTime lastActive;
    private String status;
}
