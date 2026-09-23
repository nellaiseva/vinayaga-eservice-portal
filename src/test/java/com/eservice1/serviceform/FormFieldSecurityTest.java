package com.eservice1.serviceform;

import com.eservice1.config.JwtAuthenticationEntryPoint;
import com.eservice1.config.JwtFilter;
import com.eservice1.config.JwtService;
import com.eservice1.config.SecurityConfig;
import com.eservice1.customer.controller.CustomerFormFieldController;
import com.eservice1.customer.service.CustomerFormFieldService;
import com.eservice1.serviceform.controller.ServiceFormFieldController;
import com.eservice1.serviceform.service.ServiceFormFieldService;
import com.eservice1.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ServiceFormFieldController.class, CustomerFormFieldController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
public class FormFieldSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServiceFormFieldService serviceFormFieldService;

    @MockBean
    private CustomerFormFieldService customerFormFieldService;

    @MockBean
    private JwtFilter jwtFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

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
    void testPublicGet_ServiceFormFields_Allowed() throws Exception {
        mockMvc.perform(get("/service-form-fields/service/1"))
                .andExpect(status().isOk());
    }

    @Test
    void testPublicGet_CustomerFormFields_Allowed() throws Exception {
        mockMvc.perform(get("/customer-form-fields"))
                .andExpect(status().isOk());
    }

    @Test
    void testAnonymousPost_ServiceFormFields_ForbiddenOrUnauthorized() throws Exception {
        mockMvc.perform(post("/service-form-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": 1, \"fieldName\": \"Test\", \"fieldType\": \"TEXT\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAnonymousDelete_ServiceFormFields_ForbiddenOrUnauthorized() throws Exception {
        mockMvc.perform(delete("/service-form-fields/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "CUSTOMER")
    void testCustomerPost_ServiceFormFields_Forbidden() throws Exception {
        mockMvc.perform(post("/service-form-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": 1, \"fieldName\": \"Test\", \"fieldType\": \"TEXT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "OWNER")
    void testOwnerPost_ServiceFormFields_Allowed() throws Exception {
        when(serviceFormFieldService.save(any())).thenReturn(null);

        mockMvc.perform(post("/service-form-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\": 1, \"fieldName\": \"Test\", \"fieldType\": \"TEXT\"}"))
                .andExpect(status().isOk());
    }
}
