package com.taskflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSettingsRequest {

    @NotNull(message = "notificationsEnabled is required")
    private Boolean notificationsEnabled;

    @NotNull(message = "darkMode is required")
    private Boolean darkMode;

    private String timezone;
}
