package com.eservice1.user;

import com.eservice1.common.Role;
import com.eservice1.config.JwtAuthenticationEntryPoint;
import com.eservice1.config.JwtFilter;
import com.eservice1.config.JwtService;
import com.eservice1.config.SecurityConfig;
import com.eservice1.user.controller.AuthController;
import com.eservice1.user.controller.UserController;
import com.eservice1.user.entity.User;
import com.eservice1.user.repository.UserRepository;
import com.eservice1.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {UserController.class, AuthController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
public class UserDataExposureTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtFilter jwtFilter;

    @MockBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            HttpServletRequest req = invocation.getArgument(0);
            HttpServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "OWNER")
    void testGetUsers_DoesNotExposePasswordOrHash() throws Exception {
        User user = new User();
        user.setName("Admin User");
        user.setPhoneNumber("9999999999");
        user.setPassword("$2a$10$e8wB6sM5gZ7r1z9K3uX6lO5rJqP7xYz9mQ1aBcDeFgHiJkLmNoPq");
        user.setRole(Role.OWNER);

        when(userRepository.findAll()).thenReturn(List.of(user));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Admin User"))
                .andExpect(jsonPath("$[0].phoneNumber").value("9999999999"))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("$2a$10$"))));
    }

    @Test
    void testUserEntity_JacksonSerialization_DoesNotIncludePassword() throws Exception {
        User user = new User();
        user.setName("Test Employee");
        user.setPhoneNumber("8888888888");
        user.setPassword("$2a$10$secretbcryptpasswordhash1234567890");
        user.setRole(Role.EMPLOYEE);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(user);

        assertFalse(json.contains("password"), "Serialized JSON must not contain password field");
        assertFalse(json.contains("secretbcryptpasswordhash"), "Serialized JSON must not contain password hash");
    }
}
