package com.taskflow;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.dto.CommentRequest;
import com.taskflow.dto.RegisterRequest;
import com.taskflow.dto.TaskDTO;
import com.taskflow.model.Role;
import com.taskflow.model.TaskPriority;
import com.taskflow.model.TaskStatus;
import com.taskflow.model.User;
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
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Integration tests for all Expansion features (F-EXT-01 to F-EXT-06).

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.transaction.annotation.Transactional
class ExpansionFeatureTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskCommentRepository commentRepository;
    @Autowired private ActivityLogRepository activityLogRepository;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        activityLogRepository.deleteAll();
        commentRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();
        
        // Register and upgrade to ADMIN to bypass Team restrictions on task assignments
        AuthControllerTests.registerAndGetToken(mockMvc, objectMapper);
        User testUser = userRepository.findByEmail("testuser@example.com").orElseThrow();
        testUser.setRole(Role.ADMIN);
        userRepository.save(testUser);
        
        // Re-login to get a token with MANAGER role
        com.taskflow.dto.LoginRequest login = com.taskflow.dto.LoginRequest.builder()
                .email("testuser@example.com")
                .password("password123")
                .build();
        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        token = objectMapper.readTree(loginRes.getResponse().getContentAsString()).get("token").asText();
    }

    // F-EXT-01: TASK COMMENTS

    @Test
    @Order(1)
    @DisplayName("TC-C01: Post valid comment → 201 with author + timestamp")
    void postValidComment() throws Exception {
        Long taskId = createTask("Commentable Task");

        CommentRequest req = CommentRequest.builder().body("This is a comment").build();

        mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.body").value("This is a comment"))
                .andExpect(jsonPath("$.authorName").value("TestUser"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.taskId").value(taskId));
    }

    @Test
    @Order(2)
    @DisplayName("TC-C02: Post empty comment → 400")
    void postEmptyComment() throws Exception {
        Long taskId = createTask("Task");
        CommentRequest req = CommentRequest.builder().body("").build();

        mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("TC-C03: Delete own comment → 204")
    void deleteOwnComment() throws Exception {
        Long taskId = createTask("Task");
        Long commentId = postComment(taskId, "To be deleted");

        mockMvc.perform(delete("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(4)
    @DisplayName("TC-C04: Delete someone else's comment → 403")
    void deleteOthersComment() throws Exception {
        Long taskId = createTask("Task");
        Long commentId = postComment(taskId, "My comment");

        // Register user2
        String user2Token = registerUser2();

        mockMvc.perform(delete("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("TC-C05: View comments in chronological order")
    void viewCommentsChronological() throws Exception {
        Long taskId = createTask("Task");
        postComment(taskId, "First comment");
        postComment(taskId, "Second comment");
        postComment(taskId, "Third comment");

        mockMvc.perform(get("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].body").value("First comment"))
                .andExpect(jsonPath("$[1].body").value("Second comment"))
                .andExpect(jsonPath("$[2].body").value("Third comment"));
    }
    // F-EXT-02: TASK ASSIGNMENT

    @Test
    @Order(6)
    @DisplayName("TC-A01: Create task with assignee → name shown")
    void createTaskWithAssignee() throws Exception {
        Long devUserId = createDeveloperUser();

        TaskDTO task = TaskDTO.builder()
                .title("Assigned Task")
                .dueDate(LocalDate.now().plusDays(5))
                .assigneeIds(List.of(devUserId))
                .build();

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assigneeIds[0]").value(devUserId))
                .andExpect(jsonPath("$.assigneeNames[0]").value("DevUser"));
    }

    @Test
    @Order(7)
    @DisplayName("TC-A02: Create task without assignee → null")
    void createTaskNoAssignee() throws Exception {
        TaskDTO task = TaskDTO.builder()
                .title("Unassigned Task")
                .dueDate(LocalDate.now().plusDays(5))
                .build();

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assigneeIds").isEmpty())
                .andExpect(jsonPath("$.assigneeNames").isEmpty());
    }

    @Test
    @Order(8)
    @DisplayName("TC-A04: Assigned to Me filter")
    void assignedToMeFilter() throws Exception {
        // Register a DEVELOPER user and get their token + id
        RegisterRequest devReq = RegisterRequest.builder()
                .username("DevUser").email("dev@example.com").password("password123").build();
        MvcResult devResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devReq)))
                .andExpect(status().isCreated()).andReturn();
        String devToken = objectMapper.readTree(devResult.getResponse().getContentAsString()).get("token").asText();
        Long devUserId = userRepository.findByEmail("dev@example.com").orElseThrow().getId();

        // MANAGER creates one task assigned to developer and one unassigned
        TaskDTO assigned = TaskDTO.builder().title("Mine").dueDate(LocalDate.now().plusDays(1)).assigneeIds(List.of(devUserId)).build();
        TaskDTO unassigned = TaskDTO.builder().title("Not mine").dueDate(LocalDate.now().plusDays(1)).build();

        mockMvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(assigned)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(unassigned)))
                .andExpect(status().isCreated());

        // Developer checks tasks assigned to them
        mockMvc.perform(get("/api/tasks/assigned-to-me")
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Mine"));
    }

    @Test
    @Order(9)
    @DisplayName("TC-A05: GET /api/users returns all users without passwords")
    void getUsersList() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].username").isNotEmpty())
                .andExpect(jsonPath("$[0].email").isNotEmpty())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    // F-EXT-03: PRIORITY LEVEL

    @Test
    @Order(10)
    @DisplayName("TC-P01: Default priority is MEDIUM")
    void defaultPriority() throws Exception {
        TaskDTO task = TaskDTO.builder().title("No priority set").dueDate(LocalDate.now().plusDays(1)).build();

        mockMvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    @Order(11)
    @DisplayName("TC-P05: Filter by priority ?priority=HIGH")
    void filterByPriority() throws Exception {
        createTaskWithPriority("High Task", TaskPriority.HIGH);
        createTaskWithPriority("Low Task", TaskPriority.LOW);

        mockMvc.perform(get("/api/tasks?priority=HIGH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("High Task"));
    }

    @Test
    @Order(12)
    @DisplayName("Combined filter: ?status=PENDING&priority=HIGH")
    void combinedFilter() throws Exception {
        createTaskWithStatusAndPriority("Match", TaskStatus.PENDING, TaskPriority.HIGH);
        createTaskWithStatusAndPriority("No match status", TaskStatus.COMPLETED, TaskPriority.HIGH);
        createTaskWithStatusAndPriority("No match priority", TaskStatus.PENDING, TaskPriority.LOW);

        mockMvc.perform(get("/api/tasks?status=PENDING&priority=HIGH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Match"));
    }

    
    // F-EXT-04: DASHBOARD ANALYTICS
    

    @Test
    @Order(13)
    @DisplayName("TC-AN03 + TC-AN04: Summary returns correct counts + completion rate + overdue")
    void taskSummary() throws Exception {
        createTaskWithStatusAndPriority("T1", TaskStatus.PENDING, TaskPriority.HIGH);
        createTaskWithStatusAndPriority("T2", TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM);
        createTaskWithStatusAndPriority("T3", TaskStatus.COMPLETED, TaskPriority.LOW);
        // Create overdue task (past due date, not completed)
        TaskDTO overdue = TaskDTO.builder()
                .title("Overdue")
                .status(TaskStatus.PENDING)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.now().minusDays(3))
                .build();
        mockMvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(overdue)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tasks/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(4))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.pendingTasks").value(2))
                .andExpect(jsonPath("$.inProgressTasks").value(1))
                .andExpect(jsonPath("$.overdueTasks").value(1))
                .andExpect(jsonPath("$.completionRate").value(25.0))
                .andExpect(jsonPath("$.highPriorityTasks").value(2))
                .andExpect(jsonPath("$.mediumPriorityTasks").value(1))
                .andExpect(jsonPath("$.lowPriorityTasks").value(1));
    }

    @Test
    @Order(14)
    @DisplayName("TC-AN05: No tasks → zeroes")
    void taskSummaryEmpty() throws Exception {
        mockMvc.perform(get("/api/tasks/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.completionRate").value(0));
    }


    // F-EXT-05: ACTIVITY FEED
    

    @Test
    @Order(15)
    @DisplayName("TC-AF01: Creating a task generates TASK_CREATED entry")
    void activityAfterCreate() throws Exception {
        createTask("Activity Task");

        mockMvc.perform(get("/api/activity")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actionCode").value("TASK_CREATED"))
                .andExpect(jsonPath("$[0].message", containsString("Activity Task")));
    }

    @Test
    @Order(16)
    @DisplayName("TC-AF02: Changing status generates TASK_STATUS_CHANGED entry")
    void activityAfterStatusChange() throws Exception {
        Long taskId = createTask("Status Change");

        TaskDTO update = TaskDTO.builder()
                .title("Status Change")
                .status(TaskStatus.COMPLETED)
                .dueDate(LocalDate.now().plusDays(1))
                .build();

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/activity")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].actionCode", hasItem("TASK_STATUS_CHANGED")));
    }

    @Test
    @Order(17)
    @DisplayName("TC-AF04: Posting a comment generates COMMENT_ADDED entry")
    void activityAfterComment() throws Exception {
        Long taskId = createTask("Commented Task");
        postComment(taskId, "Hello world");

        mockMvc.perform(get("/api/activity")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actionCode").value("COMMENT_ADDED"));
    }

    @Test
    @Order(18)
    @DisplayName("TC-AF05: Deleting a task generates TASK_DELETED entry with null taskId")
    void activityAfterDelete() throws Exception {
        Long taskId = createTask("Delete Me");

        mockMvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/activity")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actionCode").value("TASK_DELETED"))
                .andExpect(jsonPath("$[0].taskId").isEmpty());
    }


    // F-EXT-06: DUE DATE ALERTS

    @Test
    @Order(19)
    @DisplayName("TC-DD01: Task overdue, not done → overdue=true")
    void overdueNotDone() throws Exception {
        TaskDTO task = TaskDTO.builder()
                .title("Overdue Task")
                .status(TaskStatus.PENDING)
                .dueDate(LocalDate.now().minusDays(2))
                .build();

        MvcResult res = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated())
                .andReturn();

        Long taskId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(true));
    }

    @Test
    @Order(20)
    @DisplayName("TC-DD02: Task overdue but COMPLETED → overdue=false")
    void overdueButCompleted() throws Exception {
        TaskDTO task = TaskDTO.builder()
                .title("Done On Time")
                .status(TaskStatus.COMPLETED)
                .dueDate(LocalDate.now().minusDays(2))
                .build();

        MvcResult res = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated())
                .andReturn();

        Long taskId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    @Order(21)
    @DisplayName("TC-DD04: Task due in future → overdue=false")
    void futureDueDate() throws Exception {
        Long taskId = createTask("Future Task");

        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(false));
    }

    // HELPERS
 

    private Long createTask(String title) throws Exception {
        TaskDTO task = TaskDTO.builder()
                .title(title)
                .status(TaskStatus.PENDING)
                .priority(TaskPriority.MEDIUM)
                .dueDate(LocalDate.now().plusDays(7))
                .build();

        MvcResult res = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private void createTaskWithPriority(String title, TaskPriority priority) throws Exception {
        TaskDTO task = TaskDTO.builder().title(title).priority(priority).dueDate(LocalDate.now().plusDays(1)).build();
        mockMvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated());
    }

    private void createTaskWithStatusAndPriority(String title, TaskStatus status, TaskPriority priority) throws Exception {
        TaskDTO task = TaskDTO.builder().title(title).status(status).priority(priority).dueDate(LocalDate.now().plusDays(1)).build();
        mockMvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated());
    }

    private Long postComment(Long taskId, String body) throws Exception {
        CommentRequest req = CommentRequest.builder().body(body).build();
        MvcResult res = mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private String registerUser2() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("User Two").email("user2@example.com").password("password123").build();
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private Long createDeveloperUser() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("DevUser").email("dev@example.com").password("password123").build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
        return userRepository.findByEmail("dev@example.com").orElseThrow().getId();
    }
}
