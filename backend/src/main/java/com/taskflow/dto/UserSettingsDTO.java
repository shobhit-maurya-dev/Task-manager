package com.taskflow.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserSettingsDTO {
    private Long userId;
    private String timezone;
    private Boolean notificationsEnabled;
    private Boolean darkMode;
}
