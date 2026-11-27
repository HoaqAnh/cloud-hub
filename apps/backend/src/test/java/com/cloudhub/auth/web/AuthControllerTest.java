package com.cloudhub.auth.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudhub.auth.application.AuthService;
import com.cloudhub.auth.persistence.User;
import com.cloudhub.auth.persistence.UserRepository;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(AuthService.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository users;

    @Test
    void loginReturnsUnauthorizedWhenUsernameDoesNotExist() throws Exception {
        when(users.findByUsername("admin")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturnsUnauthorizedWhenPasswordDoesNotMatch() throws Exception {
        when(users.findByUsername("admin")).thenReturn(Optional.of(new User(
            UUID.randomUUID(),
            "admin",
            new BCryptPasswordEncoder().encode("configured-password")
        )));

        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }
}
