package com.fuba.automation_engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.persistence.entity.AppUserEntity;
import com.fuba.automation_engine.persistence.entity.AppUserRole;
import com.fuba.automation_engine.persistence.repository.AppUserRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetUsers() {
        userRepository.deleteAll();
    }

    @Test
    void shouldIssueTokenForValidCredentials() throws Exception {
        seedUser("alice", "secret-password", AppUserRole.ADMIN, true);

        MvcResult result = mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "password", "secret-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        // last_login_at should now be set
        AppUserEntity reloaded = userRepository.findByUsernameIgnoreCase("alice").orElseThrow();
        assert reloaded.getLastLoginAt() != null;
        // and the token can authenticate /me
        String body = result.getResponse().getContentAsString();
        Map<?, ?> json = objectMapper.readValue(body, Map.class);
        String token = (String) json.get("token");
        mockMvc.perform(get("/admin/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void shouldReject401ForUnknownUsername() throws Exception {
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "ghost",
                                "password", "irrelevant"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void shouldReject401ForBadPassword() throws Exception {
        seedUser("bob", "right-password", AppUserRole.OPERATOR, true);

        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "bob",
                                "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void shouldReject401ForDisabledUser() throws Exception {
        seedUser("carol", "right-password", AppUserRole.VIEWER, false);

        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "carol",
                                "password", "right-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void shouldReject401ForBlankUsernameOrPassword() throws Exception {
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(get("/admin/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private void seedUser(String username, String plaintext, AppUserRole role, boolean enabled) {
        AppUserEntity user = new AppUserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(plaintext));
        user.setRole(role);
        user.setEnabled(enabled);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }
}
