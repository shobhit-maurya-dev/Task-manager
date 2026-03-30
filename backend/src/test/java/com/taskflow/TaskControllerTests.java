package com.taskflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.dto.RegisterRequest;
import com.taskflow.dto.TaskDTO;
import com.taskflow.model.TaskPriority;
import com.taskflow.model.TaskStatus;
import com.taskflow.repository.ActivityLogRepository;
import com.taskflow.repository.TaskCommentRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
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
class TaskControllerTests {
    // Static helper for use in other test classes
    static Long createSampleTaskStatic(MockMvc mockMvc, ObjectMapper objectMapper, String token, String title) throws Exception {
        TaskDTO task = TaskDTO.builder()
                .title(title)
                .status(TaskStatus.PENDING)
                .priority(TaskPriority.MEDIUM)
                .dueDate(LocalDate.now().plusDays(7))
                .build();

        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private TaskCommentRepository commentRepository;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        activityLogRepository.deleteAll();
        commentRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();
        token = AuthControllerTests.registerAndGetToken(mockMvc, objectMapper);
    }

    // ─── CREATE Task Tests 

    @Test
    @Order(1)
    @DisplayName("FR-TASK-01: Create task returns 201 with task data")
    void createTaskSuccess() throws Exception {
        TaskDTO task = TaskDTO.builder()
                .title("My First Task")
                .description("Description for task")
                .status(TaskStatus.PENDING)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.now().plusDays(7))
                .build();

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("My First Task"))
                .andExpect(jsonPath("$.description").value("Description for task"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueDate").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("FR-TASK-01: Default status is PENDING when not provided")
    void createTaskDefaultStatus() throws Exception {
        TaskDTO task = TaskDTO.builder()
                .title("Task without status")
                .dueDate(LocalDate.now().plusDays(3))
                .build();

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    @Order(3)
    @DisplayName("FR-TASK-01: Missing title returns 400")
    void createTaskMissingTitle() throws Exception {
        TaskDTO task = TaskDTO.builder()
                .description("No title")
                .dueDate(LocalDate.now().plusDays(1))
                .build();

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("NFR-05: Unauthenticated request returns 401")
    void createTaskWithoutAuth() throws Exception {
        TaskDTO task = TaskDTO.builder()
                .title("Unauthorized Task")
                .dueDate(LocalDate.now().plusDays(1))
                .build();

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET Tasks Tests 

    @Test
    @Order(5)
    @DisplayName("FR-TASK-02: Get all tasks for authenticated user")
    void getAllTasks() throws Exception {
        // Create two tasks
        createSampleTask("Task 1", TaskStatus.PENDING);
        createSampleTask("Task 2", TaskStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").isNotEmpty())
                .andExpect(jsonPath("$[1].title").isNotEmpty());
    }

    @Test
    @Order(6)
    @DisplayName("FR-TASK-02: Empty task list returns 200 with empty array")
    void getAllTasksEmpty() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Order(7)
    @DisplayName("FR-TASK-02: Filter tasks by status")
    void getTasksFilteredByStatus() throws Exception {
        createSampleTask("Pending Task", TaskStatus.PENDING);
        createSampleTask("Done Task", TaskStatus.COMPLETED);

        mockMvc.perform(get("/api/tasks?status=PENDING")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Pending Task"));
    }

    @Test
    @Order(8)
    @DisplayName("FR-TASK-02: Filter tasks by status for caretakers and assigned tasks")
    void getTasksFilteredByStatusAssignedTask() throws Exception {
        Long taskId = createSampleTask("Assigned Pending Task", TaskStatus.PENDING);

        RegisterRequest user2Req = RegisterRequest.builder()
                .username("Team Member")
                .email("member@example.com")
                .password("password123")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user2Req)))
                .andExpect(status().isCreated())
                .andReturn();

        String user2Token = objectMapper.readTree(regResult.getResponse().getContentAsString()).get("token").asText();

        // Assign task to user2 directly in the repository so we can verify filter behavior.
        com.taskflow.model.User user2 = userRepository.findByEmail("member@example.com").orElseThrow();
        com.taskflow.model.Task task = taskRepository.findById(taskId).orElseThrow();
        task.getAssignees().add(user2);
        taskRepository.save(task);

        mockMvc.perform(get("/api/tasks?status=PENDING")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Assigned Pending Task"));

        mockMvc.perform(get("/api/tasks/summary")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(1));
    }

    @Test
    @Order(9)
    @DisplayName("FR-TASK-02: Get single task by ID")
    void getTaskById() throws Exception {
        Long taskId = createSampleTask("Specific Task", TaskStatus.PENDING);

        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId))
                .andExpect(jsonPath("$.title").value("Specific Task"));
    }

    @Test
    @Order(9)
    @DisplayName("FR-TASK-02: Get non-existent task returns 404")
    void getTaskByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/tasks/99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ─── UPDATE Task Tests 

    @Test
    @Order(10)
    @DisplayName("FR-TASK-03: Update task title, status, priority")
    void updateTaskSuccess() throws Exception {
        Long taskId = createSampleTask("Original Title", TaskStatus.PENDING);

        TaskDTO update = TaskDTO.builder()
                .title("Updated Title")
                .description("Updated desc")
                .status(TaskStatus.IN_PROGRESS)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.now().plusDays(14))
                .build();

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.description").value("Updated desc"));
    }

    @Test
    @Order(11)
    @DisplayName("FR-TASK-03: Update non-existent task returns 404")
    void updateTaskNotFound() throws Exception {
        TaskDTO update = TaskDTO.builder()
                .title("Updated Title")
                .dueDate(LocalDate.now().plusDays(1))
                .build();

        mockMvc.perform(put("/api/tasks/99999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE Task Tests 

    @Test
    @Order(12)
    @DisplayName("FR-TASK-04: Delete task returns 204 No Content")
    void deleteTaskSuccess() throws Exception {
        Long taskId = createSampleTask("Task to delete", TaskStatus.PENDING);

        mockMvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(13)
    @DisplayName("FR-TASK-04: Delete non-existent task returns 404")
    void deleteTaskNotFound() throws Exception {
        mockMvc.perform(delete("/api/tasks/99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ─── Cross-user isolation test 

    @Test
    @Order(14)
    @DisplayName("NFR-05: User cannot access another user's tasks")
    void crossUserIsolation() throws Exception {
        // User 1 creates a task
        Long taskId = createSampleTask("User1 Task", TaskStatus.PENDING);

        // Register User 2 and get their token
        RegisterRequest user2Req = RegisterRequest.builder()
                .username("User Two")
                .email("user2@example.com")
                .password("password123")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user2Req)))
                .andExpect(status().isCreated())
                .andReturn();

        String user2Token = objectMapper.readTree(
                regResult.getResponse().getContentAsString()).get("token").asText();

        // User 2 tries to access User 1's task → 404
        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());

        // User 2 sees empty task list
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ─── Helper 

    private Long createSampleTask(String title, TaskStatus status) throws Exception {
        TaskDTO task = TaskDTO.builder()
                .title(title)
                .status(status)
                .priority(TaskPriority.MEDIUM)
                .dueDate(LocalDate.now().plusDays(7))
                .build();

        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asLong();
    }
}
