package com.taskflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDTO {
    private Long id;
    private String name;
    private String description;
    private Long managerId;
    private String managerName;
    private LocalDateTime createdAt;

    // Optional detail fields
    private List<TeamMemberDTO> members;
    private List<TaskDTO> tasks;
}
