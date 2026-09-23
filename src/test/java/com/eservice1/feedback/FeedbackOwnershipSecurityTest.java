package com.eservice1.feedback;

import com.eservice1.common.exception.GlobalExceptionHandler;
import com.eservice1.config.JwtAuthenticationEntryPoint;
import com.eservice1.config.JwtFilter;
import com.eservice1.config.JwtService;
import com.eservice1.config.SecurityConfig;
import com.eservice1.employee.repository.EmployeeRepository;
import com.eservice1.employee.repository.TaskRepository;
import com.eservice1.feedback.controller.FeedbackController;
import com.eservice1.feedback.dto.FeedbackDTO;
import com.eservice1.feedback.entity.Feedback;
import com.eservice1.feedback.repository.FeedbackRepository;
import com.eservice1.feedback.service.FeedbackService;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.entity.RequestStatus;
import com.eservice1.submission.repository.CustomerRequestRepository;
import com.eservice1.submission.service.RequestAccessService;
import com.eservice1.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FeedbackController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, GlobalExceptionHandler.class, FeedbackOwnershipSecurityTest.TestConfig.class})
public class FeedbackOwnershipSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeedbackService feedbackService;

    @MockBean
    private CustomerRequestRepository requestRepository;

    @MockBean
    private FeedbackRepository feedbackRepository;

    @MockBean
    private TaskRepository taskRepository;

    @MockBean
    private EmployeeRepository employeeRepository;

    @MockBean
    private JwtFilter jwtFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TestConfiguration
    static class TestConfig {
        @Bean
        public RequestAccessService requestAccessService(CustomerRequestRepository requestRepository,
                                                         TaskRepository taskRepository,
                                                         EmployeeRepository employeeRepository) {
            return new RequestAccessService(requestRepository, taskRepository, employeeRepository);
        }

        @Bean
        public FeedbackService feedbackService(FeedbackRepository feedbackRepository,
                                               CustomerRequestRepository requestRepository,
                                               RequestAccessService requestAccessService) {
            return new FeedbackService(feedbackRepository, requestRepository, requestAccessService);
        }
    }

    private static final String CUSTOMER_A_PHONE = "9876543210";
    private static final String CUSTOMER_B_PHONE = "9111111111";
    private static final String EMPLOYEE_PHONE = "9222222222";
    private static final String OWNER_PHONE = "9999999999";

    private CustomerRequest requestA;
    private CustomerRequest requestIncomplete;

    @BeforeEach
    void setUp() throws Exception {
        reset(feedbackRepository, requestRepository);

        doAnswer(invocation -> {
            HttpServletRequest req = invocation.getArgument(0);
            HttpServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());

        requestA = new CustomerRequest();
        ReflectionTestUtils.setField(requestA, "id", 100L);
        requestA.setPhoneNumber(CUSTOMER_A_PHONE);
        requestA.setStatus(RequestStatus.COMPLETED);

        requestIncomplete = new CustomerRequest();
        ReflectionTestUtils.setField(requestIncomplete, "id", 200L);
        requestIncomplete.setPhoneNumber(CUSTOMER_A_PHONE);
        requestIncomplete.setStatus(RequestStatus.IN_PROGRESS);

        when(requestRepository.findById(100L)).thenReturn(Optional.of(requestA));
        when(requestRepository.findById(200L)).thenReturn(Optional.of(requestIncomplete));
        when(requestRepository.findById(999L)).thenReturn(Optional.empty());

        when(feedbackRepository.existsByRequestId(anyLong())).thenReturn(false);
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // =========================================================================
    // HTTP MockMvc Tests verifying feedback authorization & ownership
    // =========================================================================

    @Nested
    @DisplayName("HTTP Feedback Ownership Enforcement on /feedback")
    class HttpFeedbackOwnershipTests {

        @Test
        @DisplayName("Customer A submits feedback for Request A (owned & completed) -> allowed (200)")
        void customerA_submitsFeedbackForRequestA_allowed() throws Exception {
            FeedbackDTO dto = new FeedbackDTO();
            dto.setRequestId(100L);
            dto.setRating(5);
            dto.setComment("Excellent service");

            mockMvc.perform(post("/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .with(user(CUSTOMER_A_PHONE).authorities(new SimpleGrantedAuthority("CUSTOMER"))))
                    .andExpect(status().isOk());

            verify(feedbackRepository).save(any(Feedback.class));
        }

        @Test
        @DisplayName("Customer B submits feedback for Request A (Customer A's request) -> 403 Forbidden")
        void customerB_submitsFeedbackForRequestA_forbidden() throws Exception {
            FeedbackDTO dto = new FeedbackDTO();
            dto.setRequestId(100L);
            dto.setRating(5);
            dto.setComment("Trying to rate another user's request");

            mockMvc.perform(post("/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .with(user(CUSTOMER_B_PHONE).authorities(new SimpleGrantedAuthority("CUSTOMER"))))
                    .andExpect(status().isForbidden());

            verify(feedbackRepository, never()).save(any(Feedback.class));
        }

        @Test
        @DisplayName("Unauthenticated caller submits feedback -> 401 Unauthorized")
        void unauthenticated_submitsFeedback_unauthorized() throws Exception {
            FeedbackDTO dto = new FeedbackDTO();
            dto.setRequestId(100L);
            dto.setRating(5);

            mockMvc.perform(post("/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isUnauthorized());

            verify(feedbackRepository, never()).save(any(Feedback.class));
        }

        @Test
        @DisplayName("Employee caller submits feedback -> 403 Forbidden")
        void employee_submitsFeedback_forbidden() throws Exception {
            FeedbackDTO dto = new FeedbackDTO();
            dto.setRequestId(100L);
            dto.setRating(5);

            mockMvc.perform(post("/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .with(user(EMPLOYEE_PHONE).authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                    .andExpect(status().isForbidden());

            verify(feedbackRepository, never()).save(any(Feedback.class));
        }

        @Test
        @DisplayName("Owner caller submits feedback -> 403 Forbidden")
        void owner_submitsFeedback_forbidden() throws Exception {
            FeedbackDTO dto = new FeedbackDTO();
            dto.setRequestId(100L);
            dto.setRating(5);

            mockMvc.perform(post("/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .with(user(OWNER_PHONE).authorities(new SimpleGrantedAuthority("OWNER"))))
                    .andExpect(status().isForbidden());

            verify(feedbackRepository, never()).save(any(Feedback.class));
        }

        @Test
        @DisplayName("Customer submits feedback for nonexistent request -> 404 Not Found (safe failure)")
        void customer_submitsFeedbackNonexistentRequest_notFound() throws Exception {
            FeedbackDTO dto = new FeedbackDTO();
            dto.setRequestId(999L);
            dto.setRating(5);

            mockMvc.perform(post("/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .with(user(CUSTOMER_A_PHONE).authorities(new SimpleGrantedAuthority("CUSTOMER"))))
                    .andExpect(status().isNotFound());

            verify(feedbackRepository, never()).save(any(Feedback.class));
        }

        @Test
        @DisplayName("Customer submits feedback for non-completed request -> 400 Bad Request (rejected per business rules)")
        void customer_submitsFeedbackNonCompletedRequest_badRequest() throws Exception {
            FeedbackDTO dto = new FeedbackDTO();
            dto.setRequestId(200L);
            dto.setRating(4);

            mockMvc.perform(post("/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .with(user(CUSTOMER_A_PHONE).authorities(new SimpleGrantedAuthority("CUSTOMER"))))
                    .andExpect(status().isBadRequest());

            verify(feedbackRepository, never()).save(any(Feedback.class));
        }
    }

    // =========================================================================
    // Service-level Unit Tests verifying authorization checks
    // =========================================================================

    @Nested
    @DisplayName("Service-level Feedback Ownership Checks")
    class ServiceLevelFeedbackTests {

        private Authentication authCustomerA;
        private Authentication authCustomerB;

        @BeforeEach
        void initAuth() {
            authCustomerA = new UsernamePasswordAuthenticationToken(
                    CUSTOMER_A_PHONE, null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
            authCustomerB = new UsernamePasswordAuthenticationToken(
                    CUSTOMER_B_PHONE, null, List.of(new SimpleGrantedAuthority("CUSTOMER")));
        }

        @Test
        @DisplayName("submitFeedback throws AccessDeniedException when Customer B submits for Request A")
        void submitFeedbackThrowsForCustomerB() {
            FeedbackDTO dto = new FeedbackDTO();
            dto.setRequestId(100L);
            dto.setRating(5);

            assertThrows(AccessDeniedException.class, () ->
                    feedbackService.submitFeedback(dto, authCustomerB));
        }

        @Test
        @DisplayName("submitFeedback succeeds when Customer A submits for Request A")
        void submitFeedbackSucceedsForCustomerA() {
            FeedbackDTO dto = new FeedbackDTO();
            dto.setRequestId(100L);
            dto.setRating(5);
            dto.setComment("Great job!");

            Feedback feedback = feedbackService.submitFeedback(dto, authCustomerA);
            assertNotNull(feedback);
            assertEquals(requestA, feedback.getRequest());
        }
    }
}
