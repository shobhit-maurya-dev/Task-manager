package com.taskflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberDTO {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String memberType;
    @JsonProperty("isLeader")
    @Builder.Default
    private boolean isLeader = false;

    @JsonProperty("isLeader")
    public boolean getIsLeader() {
        return isLeader;
    }

    @JsonProperty("isLeader")
    public void setIsLeader(boolean leader) {
        this.isLeader = leader;
    }
    private LocalDateTime joinedAt;
}
