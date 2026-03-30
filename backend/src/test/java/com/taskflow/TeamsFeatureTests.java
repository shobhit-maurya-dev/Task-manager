package com.taskflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.dto.TeamDTO;
import com.taskflow.dto.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeamsFeatureTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.taskflow.repository.UserRepository userRepository;
    @Autowired private com.taskflow.repository.TeamMemberRepository teamMemberRepository;
    @Autowired private com.taskflow.repository.TeamRepository teamRepository;
    @Autowired private com.taskflow.repository.TaskRepository taskRepository;
    @Autowired private com.taskflow.repository.SubtaskRepository subtaskRepository;
    @Autowired private com.taskflow.repository.ActivityLogRepository activityLogRepository;

    private String adminToken;
    private String managerToken;
    private String memberToken;

    @BeforeEach
    void setUp() throws Exception {
        subtaskRepository.deleteAll();
        taskRepository.deleteAll();
        activityLogRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
        adminToken = registerAndGetToken("admin","admin@example.com","password", null);
        // manually elevate admin user in repository
        var adminUser = userRepository.findByEmail("admin@example.com").get();
        adminUser.setRole(com.taskflow.model.Role.ADMIN);
        userRepository.save(adminUser);
        // re-login to get proper token
        adminToken = loginAndGetToken("admin@example.com","password");

        managerToken = registerAndGetToken("mgr","mgr@example.com","password", null);
        // manually elevate manager
        var mgrUser = userRepository.findByEmail("mgr@example.com").get();
        mgrUser.setRole(com.taskflow.model.Role.MANAGER);
        userRepository.save(mgrUser);
        managerToken = loginAndGetToken("mgr@example.com","password");

        memberToken = registerAndGetToken("mem","mem@example.com","password", null);
    }

    private String registerAndGetToken(String username, String email, String pwd, String memberType) throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username(username)
                .email(email)
                .password(pwd)
                .memberType(memberType)
                .build();
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private void promoteRole(String adminToken, Long userId, String newRole) throws Exception {
        mockMvc.perform(put("/api/users/" + userId + "/role")
                        .header("Authorization","Bearer "+adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", newRole))))
                .andExpect(status().isOk());
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        var req = Map.of("email", email, "password", password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @Order(1)
    @DisplayName("TC-T01: Manager creates team and appears in list")
    void managerCreatesTeam() throws Exception {
        TeamDTO dto = TeamDTO.builder().name("Alpha").description("team alpha").build();
        mockMvc.perform(post("/api/teams")
                        .header("Authorization","Bearer "+managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alpha"));

        mockMvc.perform(get("/api/teams")
                        .header("Authorization","Bearer "+managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alpha"));
    }

    @Test
    @Order(3)
    @DisplayName("TC-T03: Admin can delete a team created by manager")
    void adminDeletesManagerTeam() throws Exception {
        // manager creates a second team
        TeamDTO dto = TeamDTO.builder().name("Gamma").description("team gamma").build();
        MvcResult res = mockMvc.perform(post("/api/teams")
                        .header("Authorization","Bearer "+managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long teamId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // admin removes it
        mockMvc.perform(delete("/api/teams/"+teamId)
                        .header("Authorization","Bearer "+adminToken))
                .andExpect(status().isNoContent());

        // manager list should now be empty
        mockMvc.perform(get("/api/teams")
                        .header("Authorization","Bearer "+managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("TC-T02: Member cannot create team")
    void memberCannotCreateTeam() throws Exception {
        TeamDTO dto = TeamDTO.builder().name("Beta").build();
        mockMvc.perform(post("/api/teams")
                        .header("Authorization","Bearer "+memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    @DisplayName("TC-T04: Admin can change user role using helper")
    void adminCanChangeRole() throws Exception {
        var member = userRepository.findByEmail("mem@example.com").get();
        promoteRole(adminToken, member.getId(), "MANAGER");
        // verify the change by logging in and inspecting token
        String newToken = loginAndGetToken("mem@example.com","password");
        String[] tokenParts = newToken.split("\\.");
        JsonNode tokenPayload = objectMapper.readTree(
                new String(java.util.Base64.getDecoder().decode(tokenParts[1])));
        Assertions.assertEquals("MANAGER", tokenPayload.get("role").asText());
    }

    // TC-T05: Manager adds a member to their team
    // Scenario: Manager adds a member to their own team. Should succeed (201 Created).
    @Test
    @Order(5)
    @DisplayName("TC-T05: Manager adds a member to their team")
    void managerAddsMemberToOwnTeam() throws Exception {
        // Manager creates a team
        TeamDTO dto = TeamDTO.builder().name("Delta").description("team delta").build();
        MvcResult res = mockMvc.perform(post("/api/teams")
                        .header("Authorization","Bearer "+managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long teamId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Admin creates a new user to add as member
        registerAndGetToken("newuser","newuser@example.com","password", null);
        var newUser = userRepository.findByEmail("newuser@example.com").get();

        // Manager adds the new user to their team
        mockMvc.perform(post("/api/teams/"+teamId+"/members/"+newUser.getId())
                        .header("Authorization","Bearer "+managerToken))
                .andExpect(status().isCreated());
    }

    // TC-T06: Manager tries to add a member to another manager’s team (should fail)
    // Scenario: Manager cannot add members to a team they do not manage. Should return 403.
    @Test
    @Order(6)
    @DisplayName("TC-T06: Manager cannot add member to another manager's team")
    void managerCannotAddMemberToOtherTeam() throws Exception {
        // Admin creates a team (admin is manager)
        TeamDTO dto = TeamDTO.builder().name("Omega").description("team omega").build();
        MvcResult res = mockMvc.perform(post("/api/teams")
                        .header("Authorization","Bearer "+adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long teamId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Manager tries to add a member to admin's team
        var member = userRepository.findByEmail("mem@example.com").get();
        mockMvc.perform(post("/api/teams/"+teamId+"/members/"+member.getId())
                        .header("Authorization","Bearer "+managerToken))
                .andExpect(status().isForbidden());
    }

    // TC-T07: Manager removes a member from their own team (should succeed)
    // Scenario: Manager removes a member from their own team. Should succeed (204 No Content).
    @Test
    @Order(7)
    @DisplayName("TC-T07: Manager removes a member from their team")
    void managerRemovesMemberFromOwnTeam() throws Exception {
        // Manager creates a team
        TeamDTO dto = TeamDTO.builder().name("Sigma").description("team sigma").build();
        MvcResult res = mockMvc.perform(post("/api/teams")
                        .header("Authorization","Bearer "+managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long teamId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Admin creates a new user to add as member
        registerAndGetToken("removeuser","removeuser@example.com","password", null);
        var removeUser = userRepository.findByEmail("removeuser@example.com").get();

        // Manager adds the new user
        mockMvc.perform(post("/api/teams/"+teamId+"/members/"+removeUser.getId())
                        .header("Authorization","Bearer "+managerToken))
                .andExpect(status().isCreated());

        // Manager removes the user
        mockMvc.perform(delete("/api/teams/"+teamId+"/members/"+removeUser.getId())
                        .header("Authorization","Bearer "+managerToken))
                .andExpect(status().isNoContent());
    }

    // TC-T08: Manager tries to remove themselves (should fail)
    // Scenario: Manager cannot remove themselves from their own team. Should return 400.
    @Test
    @Order(8)
    @DisplayName("TC-T08: Manager cannot remove themselves from their team")
    void managerCannotRemoveSelf() throws Exception {
        // Manager creates a team
        TeamDTO dto = TeamDTO.builder().name("Theta").description("team theta").build();
        MvcResult res = mockMvc.perform(post("/api/teams")
                        .header("Authorization","Bearer "+managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long teamId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
        var mgrUser = userRepository.findByEmail("mgr@example.com").get();

        // Manager tries to remove themselves
        mockMvc.perform(delete("/api/teams/"+teamId+"/members/"+mgrUser.getId())
                        .header("Authorization","Bearer "+managerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(9)
    @DisplayName("TC-T09: Team detail includes members and tasks")
    void teamDetailIncludesMembersAndTasks() throws Exception {
        // Manager creates a team
        TeamDTO dto = TeamDTO.builder().name("Zeta").description("team zeta").build();
        MvcResult res = mockMvc.perform(post("/api/teams")
                        .header("Authorization","Bearer "+managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long teamId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Add member to team
        String newUserToken = registerAndGetToken("member2", "member2@example.com", "password", null);
        var member2 = userRepository.findByEmail("member2@example.com").get();
        mockMvc.perform(post("/api/teams/" + teamId + "/members/" + member2.getId())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isCreated());

        // Member creates a task assigned to the team
        com.taskflow.dto.TaskDTO taskDto = com.taskflow.dto.TaskDTO.builder()
                .title("Team Task")
                .teamId(teamId)
                .build();
        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + newUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskDto)))
                .andExpect(status().isCreated());

        // Manager fetches team details and sees the member and the task
        mockMvc.perform(get("/api/teams/" + teamId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.tasks", hasSize(1)))
                .andExpect(jsonPath("$.tasks[0].title").value("Team Task"));

        // Another unrelated user should not see the task
        String otherToken = registerAndGetToken("other", "other@example.com", "password", null);
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", not(hasItem("Team Task"))));
    }
}
