package com.taskflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.dto.LoginRequest;
import com.taskflow.dto.RegisterRequest;
import com.taskflow.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private static final String AUTH_BASE = "/api/auth";

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    // ─── Registration Tests 

    @Test
    @Order(1)
    @DisplayName("FR-AUTH-01: Successful user registration returns 201 + JWT")
    void registerSuccess() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.username").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @Order(2)
    @DisplayName("FR-AUTH-01: Duplicate email returns 400")
    void registerDuplicateEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();

        // First registration
        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second registration with same email
        RegisterRequest dup = RegisterRequest.builder()
                .username("Jane Doe")
                .email("john@example.com")
                .password("password456")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already in use"));
    }

    @Test
    @Order(3)
    @DisplayName("FR-AUTH-01: Duplicate username returns 400")
    void registerDuplicateUsername() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        RegisterRequest dup = RegisterRequest.builder()
                .username("John Doe")
                .email("jane@example.com")
                .password("password456")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    @Order(4)
    @DisplayName("FR-AUTH-01: Invalid email format returns 400 validation error")
    void registerInvalidEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("John Doe")
                .email("not-an-email")
                .password("password123")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @Order(5)
    @DisplayName("FR-AUTH-01: Password < 8 chars returns validation error")
    void registerShortPassword() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("John Doe")
                .email("john@example.com")
                .password("short")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @Order(6)
    @DisplayName("FR-AUTH-01: Missing fields returns validation errors")
    void registerMissingFields() throws Exception {
        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    // ─── Login Tests

    @Test
    @Order(7)
    @DisplayName("FR-AUTH-02: Successful login returns 200 + JWT")
    void loginSuccess() throws Exception {
        // First register
        RegisterRequest regReq = RegisterRequest.builder()
                .username("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());

        // Then login using email
        LoginRequest loginReq = LoginRequest.builder()
                .email("john@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.username").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    @Order(8)
    @DisplayName("FR-AUTH-02: Login with username works")
    void loginWithUsername() throws Exception {
        // First register
        RegisterRequest regReq = RegisterRequest.builder()
                .username("Jane Doe")
                .email("jane@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());

        // Then login using username
        LoginRequest loginReq = LoginRequest.builder()
                .email("Jane Doe")
                .password("password123")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("Jane Doe"));
    }

    @Test
    @Order(9)
    @DisplayName("FR-AUTH-02: Wrong password returns 401")
    void loginWrongPassword() throws Exception {
        // Register first
        RegisterRequest regReq = RegisterRequest.builder()
                .username("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());

        // Login with wrong password
        LoginRequest loginReq = LoginRequest.builder()
                .email("john@example.com")
                .password("wrongpassword")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password. Please try again."));
    }

    @Test
    @Order(10)
    @DisplayName("FR-AUTH-02: Non-existent email returns 401")
    void loginNonExistentUser() throws Exception {
        LoginRequest loginReq = LoginRequest.builder()
                .email("nobody@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(11)
    @DisplayName("FR-AUTH-03: Change password success")
    void changePasswordSuccess() throws Exception {
        String token = registerAndGetToken(mockMvc, objectMapper, "Password User", "password@example.com", "password123", null);

        mockMvc.perform(patch("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "password123",
                                "newPassword", "NewPassword456!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        // login with new password
        LoginRequest loginReq = LoginRequest.builder()
                .email("password@example.com")
                .password("NewPassword456!")
                .build();

        mockMvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @Order(12)
    @DisplayName("FR-AUTH-03: Change password wrong current password")
    void changePasswordWrongCurrent() throws Exception {
        String token = registerAndGetToken(mockMvc, objectMapper, "Password User", "password@example.com", "password123", null);

        mockMvc.perform(patch("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "wrongpassword",
                                "newPassword", "NewPassword456!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    @Order(13)
    @DisplayName("FR-AUTH-04: Session listing and revoke token enforcement")
    void sessionListingAndRevoke() throws Exception {
        String token = registerAndGetToken(mockMvc, objectMapper, "SessUser", "sessuser@example.com", "password123", null);

        // list sessions
        MvcResult listResult = mockMvc.perform(
                        get("/api/users/me/sessions")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andReturn();

        String body = listResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode tree = objectMapper.readTree(body);
        Long sessionId = tree.get(0).get("id").asLong();

        // revoke session
        mockMvc.perform(delete("/api/users/me/sessions/" + sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // same token should now return 401
        mockMvc.perform(get("/api/users/me/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ─── Helper: get JWT token for authenticated tests 

    static String registerAndGetToken(MockMvc mockMvc, ObjectMapper mapper, String username, String email, String password, String memberType) throws Exception {
        String finalUsername = username != null ? username : "TestUser";
        String finalEmail = email != null ? email : "testuser@example.com";
        String finalPassword = password != null ? password : "password123";
        RegisterRequest regReq = RegisterRequest.builder()
                .username(finalUsername)
                .email(finalEmail)
                .password(finalPassword)
                .memberType(memberType)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    // Overload for legacy tests: no args, returns token for TestUser
    static String registerAndGetToken(MockMvc mockMvc, ObjectMapper mapper) throws Exception {
        return registerAndGetToken(mockMvc, mapper, null, null, null, null);
    }
}
