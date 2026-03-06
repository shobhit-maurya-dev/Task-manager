package com.taskflow.service;

import com.taskflow.config.JwtTokenProvider;
import com.taskflow.dto.AuthResponse;
import com.taskflow.dto.LoginRequest;
import com.taskflow.dto.RegisterRequest;
import com.taskflow.exception.BadRequestException;
import com.taskflow.model.Role;
import com.taskflow.model.User;
import com.taskflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;


    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already in use");
        }

        // Determine role: only DEVELOPER or TESTER allowed at registration
        Role role = Role.DEVELOPER;
        if (request.getRole() != null) {
            try {
                Role requested = Role.valueOf(request.getRole().toUpperCase());
                if (requested == Role.DEVELOPER || requested == Role.TESTER) {
                    role = requested;
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid role string → keep default DEVELOPER
            }
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .build();

        User savedUser = userRepository.save(user);

        // Auto-login after registration — JWT subject is email
        String token = tokenProvider.generateTokenFromUsername(savedUser.getEmail(), savedUser.getRole().name());

        return new AuthResponse(token, savedUser.getId(),
                savedUser.getUsername(), savedUser.getEmail(), savedUser.getRole().name());
    }

    //Authenticate user by email and return JWT.
     
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        String token = tokenProvider.generateTokenFromUsername(user.getEmail(), user.getRole().name());

        return new AuthResponse(token, user.getId(),
                user.getUsername(), user.getEmail(), user.getRole().name());
    }
}
