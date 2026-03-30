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
public class TaskAttachmentDTO {
    private Long id;
    private Long taskId;
    private Long uploaderId;
    private String originalName;
    private String mimeType;
    private Long fileSizeBytes;
    private LocalDateTime uploadedAt;
}
