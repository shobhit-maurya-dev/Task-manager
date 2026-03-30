package com.taskflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubtaskControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    private String token;
    private Long taskId;

    @Autowired
    private com.taskflow.repository.UserRepository userRepository;

    @Autowired
    private com.taskflow.repository.TaskRepository taskRepository;

    @Autowired
    private com.taskflow.repository.TaskCommentRepository commentRepository;

    @Autowired
    private com.taskflow.repository.ActivityLogRepository activityLogRepository;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up between tests so registration always succeeds
        activityLogRepository.deleteAll();
        commentRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();

        token = AuthControllerTests.registerAndGetToken(mockMvc, objectMapper);
        taskId = TaskControllerTests.createSampleTaskStatic(mockMvc, objectMapper, token, "Parent Task");
    }

    // TC-F3-01: Create subtask → appears in list, progress updates
    @Test
    @Order(1)
    // TC-F3-01
    void createSubtask() throws Exception {
        Map<String, Object> subtask = new HashMap<>();
        subtask.put("title", "Subtask 1");
        mockMvc.perform(post("/api/tasks/" + taskId + "/subtasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subtask)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Subtask 1"));
        mockMvc.perform(get("/api/tasks/" + taskId + "/subtasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Subtask 1"));
        mockMvc.perform(get("/api/tasks/" + taskId + "/subtasks/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.completed").value(0));
    }

    // TC-F3-02: Toggle complete → DB updated, progress bar changes
    @Test
    @Order(2)
    // TC-F3-02
    void toggleSubtaskComplete() throws Exception {
        Long subtaskId = createSubtaskAndGetId("Toggle Me");
        mockMvc.perform(patch("/api/subtasks/" + subtaskId + "/toggle")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true));
        mockMvc.perform(get("/api/tasks/" + taskId + "/subtasks/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.completed").value(1));
    }

    // TC-F3-03: All complete → green bar, “All done!”
    @Test
    @Order(3)
    // TC-F3-03
    void allSubtasksComplete() throws Exception {
        Long s1 = createSubtaskAndGetId("A");
        Long s2 = createSubtaskAndGetId("B");
        mockMvc.perform(patch("/api/subtasks/" + s1 + "/toggle")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/subtasks/" + s2 + "/toggle")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks/" + taskId + "/subtasks/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.completed").value(2))
                .andExpect(jsonPath("$.allDone").value(true));
    }

    // TC-F3-04: Delete subtask → recalculates progress
    @Test
    @Order(4)
    // TC-F3-04
    void deleteSubtask() throws Exception {
        Long subtaskId = createSubtaskAndGetId("Delete Me");
        mockMvc.perform(delete("/api/subtasks/" + subtaskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tasks/" + taskId + "/subtasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // TC-F3-05: Parent delete cascades subtasks
    @Test
    @Order(5)
    // TC-F3-05
    void parentDeleteCascadesSubtasks() throws Exception {
        Long subtaskId = createSubtaskAndGetId("Cascade");
        mockMvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/subtasks/" + subtaskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // TC-F3-06: Viewer role: read-only
    @Test
    @Order(6)
    // TC-F3-06
    void viewerRoleReadOnly() throws Exception {
        String viewerToken = AuthControllerTests.registerAndGetToken(mockMvc, objectMapper, "ViewerTwo", "viewer2@example.com", "password", "VIEWER");
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Nope");
        mockMvc.perform(post("/api/tasks/" + taskId + "/subtasks")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    // Helper
    Long createSubtaskAndGetId(String title) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        MvcResult res = mockMvc.perform(post("/api/tasks/" + taskId + "/subtasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }
}
