package com.taskflow.dto;

import lombok.Data;

@Data
public class FirebaseTokenRequest {
    private String idToken;
    private String email;
}
