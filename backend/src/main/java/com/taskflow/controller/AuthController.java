package com.taskflow.controller;

import com.taskflow.dto.AuthResponse;
import com.taskflow.dto.LoginRequest;
import com.taskflow.dto.RegisterRequest;
import com.taskflow.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    //  POST /api/auth/register
    //   Register a new user and return JWT token.
    
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String platform = httpRequest.getHeader("X-Platform");
        AuthResponse response = authService.register(request, userAgent, platform);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }


    //  POST /api/auth/login
    //  Authenticate user and return JWT token.
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String platform = httpRequest.getHeader("X-Platform");
        AuthResponse response = authService.login(request, userAgent, platform);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/firebase-login")
    public ResponseEntity<AuthResponse> firebaseLogin(@RequestBody java.util.Map<String, String> payload, HttpServletRequest httpRequest) {
        String idToken = payload.get("token");
        String userAgent = httpRequest.getHeader("User-Agent");
        String platform = httpRequest.getHeader("X-Platform");
        AuthResponse response = authService.firebaseLogin(idToken, userAgent, platform);
        return ResponseEntity.ok(response);
    }
}
