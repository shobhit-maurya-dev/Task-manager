package com.taskflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.dto.RegisterRequest;
import com.taskflow.dto.TimeEntryRequest;
import com.taskflow.dto.TaskDTO;
import com.taskflow.model.Role;
import com.taskflow.model.TaskStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.transaction.annotation.Transactional
class TimeTrackingControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private Long taskId;

    @Autowired
    private com.taskflow.repository.UserRepository userRepository;

    @Autowired
    private com.taskflow.repository.TeamRepository teamRepository;

    @Autowired
    private com.taskflow.repository.TaskRepository taskRepository;

    @Autowired
    private com.taskflow.repository.ActivityLogRepository activityLogRepository;

    @BeforeEach
    void setUp() throws Exception {
        activityLogRepository.deleteAll();
        taskRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();

        token = AuthControllerTests.registerAndGetToken(mockMvc, objectMapper);
        taskId = TaskControllerTests.createSampleTaskStatic(mockMvc, objectMapper, token, "Time Task");
    }

    @Test
    @Order(1)
    @DisplayName("TT-01: Start and stop timer creates time log")
    void startStopTimerCreatesTimeLog() throws Exception {
        // Start timer
        mockMvc.perform(post("/api/tasks/" + taskId + "/timer/start")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId));

        // Stop timer
        MvcResult stop = mockMvc.perform(post("/api/tasks/" + taskId + "/timer/stop")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutes", greaterThan(0)))
                .andReturn();

        Long entryId = objectMapper.readTree(stop.getResponse().getContentAsString()).get("id").asLong();

        // Verify total
        mockMvc.perform(get("/api/tasks/" + taskId + "/time-logs/total")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutes", greaterThan(0)));

        // Cannot delete timer-generated entry
        mockMvc.perform(delete("/api/time-logs/" + entryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(2)
    @DisplayName("TT-02: Manual time log can be created and deleted")
    void manualTimeLogCreateDelete() throws Exception {
        TimeEntryRequest req = new TimeEntryRequest();
        req.setMinutes(15);
        req.setLogDate(LocalDate.now());
        req.setNote("Manual entry");

        MvcResult res = mockMvc.perform(post("/api/tasks/" + taskId + "/time-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.minutes").value(15))
                .andReturn();

        Long entryId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Listing should include it
        mockMvc.perform(get("/api/tasks/" + taskId + "/time-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Delete it
        mockMvc.perform(delete("/api/time-logs/" + entryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(3)
    @DisplayName("TT-03: Viewer role cannot log time even when assigned")
    void viewerRoleCannotLogTime() throws Exception {
        RegisterRequest viewerReg = RegisterRequest.builder()
                .username("ReadonlyViewer")
                .email("readonly-viewer@example.com")
                .password("password123")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(viewerReg)))
                .andExpect(status().isCreated())
                .andReturn();

        String viewerToken = objectMapper.readTree(regResult.getResponse().getContentAsString()).get("token").asText();
        com.taskflow.model.User viewer = userRepository.findByEmail("readonly-viewer@example.com").orElseThrow();
        viewer.setRole(Role.VIEWER);
        userRepository.save(viewer);

        com.taskflow.model.Task existing = taskRepository.findById(taskId).orElseThrow();
        existing.getAssignees().add(viewer);
        taskRepository.save(existing);

        mockMvc.perform(post("/api/tasks/" + taskId + "/timer/start")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        TimeEntryRequest req = new TimeEntryRequest();
        req.setMinutes(10);
        req.setLogDate(LocalDate.now());
        req.setNote("Viewer cannot log");

        mockMvc.perform(post("/api/tasks/" + taskId + "/time-logs")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    @DisplayName("TT-04: Completing task auto-stops active timers")
    void completingTaskStopsActiveTimers() throws Exception {
        mockMvc.perform(post("/api/tasks/" + taskId + "/timer/start")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId));

        TaskDTO updated = TaskDTO.builder()
                .title("Time Task")
                .description("Complete now")
                .status(TaskStatus.COMPLETED)
                .priority(com.taskflow.model.TaskPriority.MEDIUM)
                .dueDate(LocalDate.now().plusDays(7))
                .build();

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/tasks/" + taskId + "/timer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks/" + taskId + "/time-logs/total")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutes", greaterThan(0)));
    }
}
