package com.taskflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskAttachmentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.taskflow.repository.UserRepository userRepository;

    @Autowired
    private com.taskflow.repository.TaskRepository taskRepository;

    @Autowired
    private com.taskflow.repository.ActivityLogRepository activityLogRepository;

    @Autowired
    private com.taskflow.repository.TaskCommentRepository commentRepository;

    private String token;
    private Long taskId;

    @BeforeEach
    void setUp() throws Exception {
        // Clean database to avoid conflicts across tests
        activityLogRepository.deleteAll();
        commentRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();

        // Register and login a user, create a task
        token = AuthControllerTests.registerAndGetToken(mockMvc, objectMapper, "AdminUser", "user@example.com", "password", "ADMIN");
        taskId = TaskControllerTests.createSampleTaskStatic(mockMvc, objectMapper, token, "Attachment Test Task");
    }

    // TC-F2-01: Valid file upload (Admin/Manager/Member)
    // Should return 201 and metadata, file stored in DB
    @Test
    @Order(1)
    // TC-F2-01
    void uploadAttachmentSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "testfile.txt", "text/plain",
                "Hello, world!".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalName").value("testfile.txt"))
                .andExpect(jsonPath("$.mimeType").value("text/plain"))
                .andExpect(jsonPath("$.fileSizeBytes").value(13));
    }

    // TC-F2-02: Upload >5MB file rejected
    @Test
    @Order(2)
    // TC-F2-02
    void uploadAttachmentTooLarge() throws Exception {
        byte[] bigFile = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "bigfile.pdf", "application/pdf", bigFile);
        mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // TC-F2-03: Disallowed MIME type rejected
    @Test
    @Order(3)
    // TC-F2-03
    void uploadAttachmentDisallowedType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/x-msdownload", new byte[100]);
        mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // TC-F2-04: List attachments (metadata only, no file_data)
    @Test
    @Order(4)
    // TC-F2-04
    void listAttachments() throws Exception {
        // Upload a file first
        MockMultipartFile file = new MockMultipartFile(
                "file", "listme.txt", "text/plain", "abc".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/tasks/" + taskId + "/attachments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalName").value("listme.txt"))
                .andExpect(jsonPath("$[0].fileData").doesNotExist());
    }

    // TC-F2-05: Download attachment returns correct file
    @Test
    @Order(5)
    // TC-F2-05
    void downloadAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "downloadme.txt", "text/plain", "download!".getBytes(StandardCharsets.UTF_8));
        MvcResult res = mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        Long attachmentId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(get("/api/attachments/" + attachmentId + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("downloadme.txt")))
                .andExpect(content().bytes("download!".getBytes(StandardCharsets.UTF_8)));
    }

    // TC-F2-06: Delete attachment (uploader/Admin/Manager)
    @Test
    @Order(6)
    // TC-F2-06
    void deleteAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "deleteme.txt", "text/plain", "bye".getBytes(StandardCharsets.UTF_8));
        MvcResult res = mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        Long attachmentId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(delete("/api/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // TC-F2-07: Viewer cannot upload or delete attachments
    @Test
    @Order(7)
    // TC-F2-07
    void viewerCannotUploadOrDelete() throws Exception {
        // Register a viewer
        String viewerToken = AuthControllerTests.registerAndGetToken(mockMvc, objectMapper, "ViewerUser", "viewer@example.com", "password", "VIEWER");
        MockMultipartFile file = new MockMultipartFile(
                "file", "viewblock.txt", "text/plain", "nope".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }

    // TC-F2-08: Sixth file rejected (max 5 per task)
    @Test
    @Order(8)
    // TC-F2-08
    void sixthFileRejected() throws Exception {
        for (int i = 1; i <= 5; i++) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "file"+i+".txt", "text/plain", ("file"+i).getBytes(StandardCharsets.UTF_8));
            mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                            .file(file)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated());
        }
        MockMultipartFile sixth = new MockMultipartFile(
                "file", "file6.txt", "text/plain", "sixth".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(sixth)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // TC-F2-09: Task delete cascades attachments
    @Test
    @Order(9)
    // TC-F2-09
    void taskDeleteCascadesAttachments() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cascade.txt", "text/plain", "cascade".getBytes(StandardCharsets.UTF_8));
        MvcResult res = mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        Long attachmentId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
        // Delete task
        mockMvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        // Attachment should be gone
        mockMvc.perform(get("/api/attachments/" + attachmentId + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
