package com.taskflow.config;

import com.taskflow.model.Role;
import com.taskflow.model.User;
import com.taskflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminSeeder.class);

    private static final String ADMIN_EMAIL = "admin@taskflow.com";
    private static final String ADMIN_USERNAME = "Admin";

    @Value("${app.admin.password}")
    private String adminPassword;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        Optional<User> existing = userRepository.findByEmail(ADMIN_EMAIL);
        if (existing.isEmpty()) {
            User admin = User.builder()
                    .username(ADMIN_USERNAME)
                    .email(ADMIN_EMAIL)
                    .password(passwordEncoder.encode(adminPassword))
                    .role(Role.ADMIN)
                    .roleChangeCount(0)
                    .build();
            userRepository.save(admin);
            logger.info("Default ADMIN account created: {}", ADMIN_EMAIL);
        } else {
            // Always sync password from env variable
            User admin = existing.get();
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
            logger.info("ADMIN password synced from env variable: {}", ADMIN_EMAIL);
        }
    }
}
