package com.taskflow.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserPasswordChangeRequest {
    private String currentPassword;
    private String newPassword;
}
