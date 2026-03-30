package com.taskflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.dto.UserSettingsRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SettingsControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @Autowired
    private com.taskflow.repository.UserRepository userRepository;

    @Autowired
    private com.taskflow.repository.TaskRepository taskRepository;

    @Autowired
    private com.taskflow.repository.SubtaskRepository subtaskRepository;

    @Autowired
    private com.taskflow.repository.ActivityLogRepository activityLogRepository;

    @BeforeEach
    void setUp() throws Exception {
        // Delete in dependency order to avoid FK constraint violations
        subtaskRepository.deleteAll();
        taskRepository.deleteAll();
        activityLogRepository.deleteAll();
        userRepository.deleteAll();
        token = AuthControllerTests.registerAndGetToken(mockMvc, objectMapper);
    }

    @Test
    @Order(1)
    @DisplayName("ST-01: Get default settings")
    void getDefaultSettings() throws Exception {
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationsEnabled").value(true))
                .andExpect(jsonPath("$.darkMode").value(false));
    }

    @Test
    @Order(2)
    @DisplayName("ST-02: Update and retrieve settings")
    void updateAndGetSettings() throws Exception {
        UserSettingsRequest req = new UserSettingsRequest();
        req.setNotificationsEnabled(false);
        req.setDarkMode(true);
        req.setTimezone("America/New_York");

        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationsEnabled").value(false))
                .andExpect(jsonPath("$.darkMode").value(true))
                .andExpect(jsonPath("$.timezone").value("America/New_York"));

        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.darkMode").value(true));
    }
}
