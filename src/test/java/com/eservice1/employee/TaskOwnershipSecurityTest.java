package com.eservice1.employee;

import com.eservice1.common.exception.GlobalExceptionHandler;
import com.eservice1.config.JwtAuthenticationEntryPoint;
import com.eservice1.config.JwtFilter;
import com.eservice1.config.JwtService;
import com.eservice1.config.SecurityConfig;
import com.eservice1.employee.controller.EmployeeTaskController;
import com.eservice1.employee.entity.Employee;
import com.eservice1.employee.entity.Priority;
import com.eservice1.employee.entity.Task;
import com.eservice1.employee.entity.TaskStatus;
import com.eservice1.employee.repository.EmployeeRepository;
import com.eservice1.employee.repository.TaskRepository;
import com.eservice1.employee.service.TaskService;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.entity.RequestStatus;
import com.eservice1.submission.repository.CustomerRequestRepository;
import com.eservice1.submission.repository.UploadedDocumentRepository;
import com.eservice1.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.eservice1.submission.entity.UploadedDocument;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmployeeTaskController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, GlobalExceptionHandler.class, TaskOwnershipSecurityTest.TestConfig.class})
public class TaskOwnershipSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskService taskService;

    @MockBean
    private TaskRepository taskRepository;

    @MockBean
    private EmployeeRepository employeeRepository;

    @MockBean
    private CustomerRequestRepository requestRepository;

    @MockBean
    private UploadedDocumentRepository uploadedDocumentRepository;

    @MockBean
    private JwtFilter jwtFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TaskService taskService(TaskRepository taskRepository,
                                       EmployeeRepository employeeRepository,
                                       CustomerRequestRepository requestRepository,
                                       UploadedDocumentRepository uploadedDocumentRepository) {
            return new TaskService(taskRepository, employeeRepository, requestRepository, uploadedDocumentRepository);
        }
    }

    private static final String EMPLOYEE_A_PHONE = "9876543210";
    private static final Long EMPLOYEE_A_ID = 10L;

    private static final String EMPLOYEE_B_PHONE = "9123456780";
    private static final Long EMPLOYEE_B_ID = 20L;

    private static final String OWNER_PHONE = "9999999999";

    private Employee employeeA;
    private Employee employeeB;
    private Task taskA;
    private CustomerRequest requestA;

    private final List<File> createdFilesToDelete = new ArrayList<>();
    private final Set<String> preExistingUploadFiles = new HashSet<>();

    @BeforeEach
    void setUp() throws Exception {
        reset(taskRepository, employeeRepository, requestRepository, uploadedDocumentRepository);

        File uploadsDir = new File(System.getProperty("user.dir"), "uploads");
        if (uploadsDir.exists() && uploadsDir.isDirectory()) {
            File[] files = uploadsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    preExistingUploadFiles.add(f.getAbsolutePath());
                }
            }
        }

        when(uploadedDocumentRepository.save(any(UploadedDocument.class))).thenAnswer(invocation -> {
            UploadedDocument doc = invocation.getArgument(0);
            if (doc != null && doc.getFilePath() != null) {
                createdFilesToDelete.add(new File(doc.getFilePath()));
            }
            return doc;
        });

        doAnswer(invocation -> {
            HttpServletRequest req = invocation.getArgument(0);
            HttpServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());

        employeeA = new Employee();
        ReflectionTestUtils.setField(employeeA, "id", EMPLOYEE_A_ID);
        employeeA.setPhoneNumber(EMPLOYEE_A_PHONE);
        employeeA.setName("Employee A");

        employeeB = new Employee();
        ReflectionTestUtils.setField(employeeB, "id", EMPLOYEE_B_ID);
        employeeB.setPhoneNumber(EMPLOYEE_B_PHONE);
        employeeB.setName("Employee B");

        requestA = new CustomerRequest();
        ReflectionTestUtils.setField(requestA, "id", 100L);
        requestA.setStatus(RequestStatus.IN_PROGRESS);

        taskA = new Task();
        ReflectionTestUtils.setField(taskA, "id", 1L);
        taskA.setEmployee(employeeA);
        taskA.setRequest(requestA);
        taskA.setStatus(TaskStatus.ACCEPTED);
        taskA.setPriority(Priority.MEDIUM);

        when(employeeRepository.findByPhoneNumber(EMPLOYEE_A_PHONE)).thenReturn(employeeA);
        when(employeeRepository.findByPhoneNumber(EMPLOYEE_B_PHONE)).thenReturn(employeeB);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(taskA));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestRepository.save(any(CustomerRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        for (File f : createdFilesToDelete) {
            if (f != null && f.exists()) {
                f.delete();
            }
        }
        createdFilesToDelete.clear();

        File uploadsDir = new File(System.getProperty("user.dir"), "uploads");
        if (uploadsDir.exists() && uploadsDir.isDirectory()) {
            File[] files = uploadsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().startsWith("RESULT_") && !preExistingUploadFiles.contains(f.getAbsolutePath())) {
                        f.delete();
                    }
                }
            }
        }
        preExistingUploadFiles.clear();
    }

    // =========================================================================
    // HTTP MockMvc Tests verifying cross-user IDOR enforcement
    // =========================================================================

    @Nested
    @DisplayName("HTTP IDOR Enforcement on /employee/tasks")
    class HttpTaskIdorTests {

        @Test
        @DisplayName("Employee A owns Task A -> accept is allowed (200)")
        void employeeA_acceptTaskA_allowed() throws Exception {
            mockMvc.perform(post("/employee/tasks/1/accept")
                            .with(user(EMPLOYEE_A_PHONE).authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Employee B accepts Task A -> 403 Forbidden")
        void employeeB_acceptTaskA_forbidden() throws Exception {
            mockMvc.perform(post("/employee/tasks/1/accept")
                            .with(user(EMPLOYEE_B_PHONE).authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Employee A owns Task A -> complete is allowed (200)")
        void employeeA_completeTaskA_allowed() throws Exception {
            mockMvc.perform(post("/employee/tasks/1/complete")
                            .with(user(EMPLOYEE_A_PHONE).authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Employee B completes Task A -> 403 Forbidden")
        void employeeB_completeTaskA_forbidden() throws Exception {
            mockMvc.perform(post("/employee/tasks/1/complete")
                            .with(user(EMPLOYEE_B_PHONE).authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Employee A owns Task A -> change priority is allowed (200)")
        void employeeA_changePriorityTaskA_allowed() throws Exception {
            mockMvc.perform(post("/employee/tasks/1/priority")
                            .param("priority", "HIGH")
                            .with(user(EMPLOYEE_A_PHONE).authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Employee B changes Task A priority -> 403 Forbidden")
        void employeeB_changePriorityTaskA_forbidden() throws Exception {
            mockMvc.perform(post("/employee/tasks/1/priority")
                            .param("priority", "HIGH")
                            .with(user(EMPLOYEE_B_PHONE).authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Employee A uploads result for Task A -> allowed (200)")
        void employeeA_uploadResultTaskA_allowed() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "result.pdf", "application/pdf", "Valid test pdf content".getBytes());

            mockMvc.perform(multipart("/employee/tasks/upload-result")
                            .file(file)
                            .param("taskId", "1")
                            .with(user(EMPLOYEE_A_PHONE).authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Employee B uploads result for Task A -> 403 Forbidden")
        void employeeB_uploadResultTaskA_forbidden() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "result.pdf", "application/pdf", "Valid test pdf content".getBytes());

            mockMvc.perform(multipart("/employee/tasks/upload-result")
                            .file(file)
                            .param("taskId", "1")
                            .with(user(EMPLOYEE_B_PHONE).authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                    .andExpect(status().isForbidden());

            verify(uploadedDocumentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Employee A can retrieve their own task list -> allowed (200)")
        void employeeA_getTasksOwnId_allowed() throws Exception {
            when(taskRepository.searchEmployeeTasks(eq(EMPLOYEE_A_ID), anyString(), anyString(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(taskA)));

            mockMvc.perform(get("/employee/tasks/" + EMPLOYEE_A_ID)
                            .with(user(EMPLOYEE_A_PHONE).authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Employee B requesting Employee A's ID -> 403 Forbidden")
        void employeeB_getTasksEmployeeA_forbidden() throws Exception {
            mockMvc.perform(get("/employee/tasks/" + EMPLOYEE_A_ID)
                            .with(user(EMPLOYEE_B_PHONE).authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OWNER retains legitimate administrative access to employee tasks -> allowed (200)")
        void owner_administrativeAccess_allowed() throws Exception {
            when(taskRepository.searchEmployeeTasks(eq(EMPLOYEE_A_ID), anyString(), anyString(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(taskA)));

            // OWNER can view Employee A's tasks
            mockMvc.perform(get("/employee/tasks/" + EMPLOYEE_A_ID)
                            .with(user(OWNER_PHONE).authorities(new SimpleGrantedAuthority("OWNER"))))
                    .andExpect(status().isOk());

            // OWNER can accept Task A
            mockMvc.perform(post("/employee/tasks/1/accept")
                            .with(user(OWNER_PHONE).authorities(new SimpleGrantedAuthority("OWNER"))))
                    .andExpect(status().isOk());

            // OWNER can complete Task A
            mockMvc.perform(post("/employee/tasks/1/complete")
                            .with(user(OWNER_PHONE).authorities(new SimpleGrantedAuthority("OWNER"))))
                    .andExpect(status().isOk());

            // OWNER can change Task A priority
            mockMvc.perform(post("/employee/tasks/1/priority")
                            .param("priority", "HIGH")
                            .with(user(OWNER_PHONE).authorities(new SimpleGrantedAuthority("OWNER"))))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // Service-level Unit Tests verifying authorization checks
    // =========================================================================

    @Nested
    @DisplayName("Service-level IDOR Authorization Checks")
    class ServiceLevelIdorTests {

        private Authentication authEmployeeA;
        private Authentication authEmployeeB;
        private Authentication authOwner;

        @BeforeEach
        void initAuth() {
            authEmployeeA = new UsernamePasswordAuthenticationToken(
                    EMPLOYEE_A_PHONE, null, List.of(new SimpleGrantedAuthority("EMPLOYEE")));
            authEmployeeB = new UsernamePasswordAuthenticationToken(
                    EMPLOYEE_B_PHONE, null, List.of(new SimpleGrantedAuthority("EMPLOYEE")));
            authOwner = new UsernamePasswordAuthenticationToken(
                    OWNER_PHONE, null, List.of(new SimpleGrantedAuthority("OWNER")));
        }

        @Test
        @DisplayName("acceptTask throws AccessDeniedException when caller is Employee B")
        void acceptTaskThrowsForEmployeeB() {
            assertThrows(AccessDeniedException.class, () -> taskService.acceptTask(1L, authEmployeeB));
        }

        @Test
        @DisplayName("acceptTask succeeds when caller is Employee A")
        void acceptTaskSucceedsForEmployeeA() {
            Task result = taskService.acceptTask(1L, authEmployeeA);
            assertNotNull(result);
            assertEquals(TaskStatus.IN_PROGRESS, result.getStatus());
        }

        @Test
        @DisplayName("completeTask throws AccessDeniedException when caller is Employee B")
        void completeTaskThrowsForEmployeeB() {
            assertThrows(AccessDeniedException.class, () -> taskService.completeTask(1L, authEmployeeB));
        }

        @Test
        @DisplayName("completeTask succeeds when caller is Employee A")
        void completeTaskSucceedsForEmployeeA() {
            Task result = taskService.completeTask(1L, authEmployeeA);
            assertNotNull(result);
            assertEquals(TaskStatus.COMPLETED, result.getStatus());
        }

        @Test
        @DisplayName("updatePriority throws AccessDeniedException when caller is Employee B")
        void updatePriorityThrowsForEmployeeB() {
            assertThrows(AccessDeniedException.class, () -> taskService.updatePriority(1L, Priority.URGENT, authEmployeeB));
        }

        @Test
        @DisplayName("updatePriority succeeds when caller is Employee A")
        void updatePrioritySucceedsForEmployeeA() {
            Task result = taskService.updatePriority(1L, Priority.URGENT, authEmployeeA);
            assertNotNull(result);
            assertEquals(Priority.URGENT, result.getPriority());
        }

        @Test
        @DisplayName("uploadResult throws AccessDeniedException for Employee B BEFORE writing file or modifying status")
        void uploadResultThrowsForEmployeeBBeforeWriting() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "result.pdf", "application/pdf", "Valid test pdf content".getBytes());

            assertThrows(AccessDeniedException.class, () -> taskService.uploadResult(1L, file, authEmployeeB));

            // Verify task was NOT completed and document was NOT saved
            assertNotEquals(TaskStatus.COMPLETED, taskA.getStatus());
            verify(uploadedDocumentRepository, never()).save(any());
        }

        @Test
        @DisplayName("getTasks throws AccessDeniedException when Employee B requests Employee A's tasks")
        void getTasksThrowsForCrossEmployeeAccess() {
            assertThrows(AccessDeniedException.class, () ->
                    taskService.getTasks(EMPLOYEE_A_ID, 0, 10, null, null, null, authEmployeeB));
        }

        @Test
        @DisplayName("OWNER can operate on Task A across all operations")
        void ownerCanOperateOnAnyTask() throws Exception {
            assertDoesNotThrow(() -> taskService.acceptTask(1L, authOwner));
            assertDoesNotThrow(() -> taskService.updatePriority(1L, Priority.HIGH, authOwner));
            assertDoesNotThrow(() -> taskService.completeTask(1L, authOwner));
        }
    }
}
