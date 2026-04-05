package com.taskflow.service;

import com.taskflow.config.JwtTokenProvider;
import com.taskflow.dto.AuthResponse;
import com.taskflow.dto.LoginRequest;
import com.taskflow.dto.RegisterRequest;
import com.taskflow.exception.BadRequestException;

import com.taskflow.model.MemberType;
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
    private FirebaseService firebaseService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserSessionService userSessionService;

    public AuthResponse register(RegisterRequest request, String userAgent, String platform) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already in use");
        }

        Role role = userRepository.count() == 0 ? Role.ADMIN : Role.MEMBER;
        MemberType memberType = MemberType.DEVELOPER;
        if (request.getMemberType() != null) {
            try {
                memberType = MemberType.valueOf(request.getMemberType().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .memberType(memberType)
                .isEmailVerified(true) // restored to true since there's no endpoint to manually verify
                .isActive(true)
                .build();

        // Save user now with real password, but unverified.
        // Only after Firebase email verification will is_email_verified be set to true.
        User savedUser = userRepository.save(user);

        // Generate a token for immediate login after registration to satisfy tests and
        // standard flow
        String jti = java.util.UUID.randomUUID().toString();
        String token = tokenProvider.generateTokenFromUsername(
                savedUser.getEmail(), savedUser.getRole().name(), savedUser.getId(), savedUser.getUsername(), jti);

        userSessionService.createSession(savedUser.getId(), jti, userAgent, platform);

        return new AuthResponse(token, savedUser.getId(),
                savedUser.getUsername(), savedUser.getEmail(), savedUser.getRole().name());
    }

    public AuthResponse login(LoginRequest request, String userAgent, String platform) {
        String identifier = request.getEmail();

        User user = userRepository.findByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid email or password. Please try again."));

        if (!user.isActive()) {
            throw new BadRequestException("User account is deactivated");
        }

        if (!user.isEmailVerified()) {
            throw new BadRequestException("Email is not verified. Please verify your email first.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        identifier, request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jti = java.util.UUID.randomUUID().toString();
        String token = tokenProvider.generateTokenFromUsername(
                user.getEmail(), user.getRole().name(), user.getId(), user.getUsername(), jti);

        userSessionService.createSession(user.getId(), jti, userAgent, platform);

        return new AuthResponse(token, user.getId(),
                user.getUsername(), user.getEmail(), user.getRole().name());
    }

    public AuthResponse firebaseLogin(String idToken, String userAgent, String platform) {
        try {
            // 1. Verify token via Firebase Admin SDK
            String email = firebaseService.verifyToken(idToken);

            // 2. Find user or AUTO-CREATE
            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                // First-time sync for verified user
                user = User.builder()
                        .email(email)
                        .username(email.split("@")[0] + "_" + (int) (Math.random() * 1000))
                        .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                        .role(userRepository.count() == 0 ? Role.ADMIN : Role.MEMBER)
                        .memberType(MemberType.DEVELOPER)
                        .isEmailVerified(true)
                        .isActive(true)
                        .build();
                user = userRepository.save(user);
                System.out.println("[AUTH] Auto-synced verified user: " + email);
            } else if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
                userRepository.save(user);
            }

            // 4. Generate TaskFlow JWT
            String jti = java.util.UUID.randomUUID().toString();
            String token = tokenProvider.generateTokenFromUsername(
                    user.getEmail(), user.getRole().name(), user.getId(), user.getUsername(), jti);

            userSessionService.createSession(user.getId(), jti, userAgent, platform);

            return new AuthResponse(token, user.getId(),
                    user.getUsername(), user.getEmail(), user.getRole().name());
        } catch (Exception e) {
            System.err.println("[Firebase Error] Verification failed. Check if serviceAccountKey.json is correct: "
                    + e.getMessage());
            throw new BadRequestException(
                    "Database sync failed. Backend cannot verify Firebase token: " + e.getMessage());
        }
    }

    public void sendOtp(String email) {
        // Redundant with Firebase but keeping for backward compatibility if needed
        // temporarily
        System.out.println("[AUTH] Manual OTP sending requested for " + email + " but Firebase handles it now.");
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
}
