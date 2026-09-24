package com.eservice1.submission;

import com.eservice1.common.Role;
import com.eservice1.employee.entity.Employee;
import com.eservice1.employee.entity.Priority;
import com.eservice1.employee.entity.Task;
import com.eservice1.employee.entity.TaskStatus;
import com.eservice1.employee.repository.EmployeeRepository;
import com.eservice1.employee.repository.TaskRepository;
import com.eservice1.service.entity.PortalService;
import com.eservice1.service.repository.PortalServiceRepository;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.entity.PaymentAuditLog;
import com.eservice1.submission.entity.PaymentStatus;
import com.eservice1.submission.entity.RequestStatus;
import com.eservice1.submission.repository.CustomerRequestRepository;
import com.eservice1.submission.repository.PaymentAuditLogRepository;
import com.eservice1.submission.service.CustomerRequestService;
import com.eservice1.user.entity.User;
import com.eservice1.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 3 — Payment Workflow Security Tests.
 *
 * Tests the hardened POST /requests/{id}/payment endpoint and the
 * underlying service-level state machine.
 *
 * OWNER user is NOT inserted into the DB to avoid conflicting with the
 * uk_users_single_owner constraint. HTTP tests use @WithMockUser;
 * service-level tests use UsernamePasswordAuthenticationToken directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class PaymentSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CustomerRequestRepository requestRepository;
    @Autowired private CustomerRequestService requestService;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private PortalServiceRepository serviceRepository;
    @Autowired private PaymentAuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // Phone numbers prefixed 8888888 to avoid collisions with other test classes
    private static final String OWNER_PHONE       = "8888888001";  // NOT inserted (avoids uk_users_single_owner)
    private static final String EMPLOYEE_PHONE    = "8888888002";
    private static final String CUSTOMER_PHONE    = "8888888003";
    private static final String OTHER_CUST_PHONE  = "8888888004";
    private static final String UNASSIGNED_EMP    = "8888888005";

    private Employee assignedEmployee;
    private Employee unassignedEmployee;
    private PortalService testService;

    @BeforeEach
    void setUp() {
        cleanTestData();

        // Minimal PortalService
        testService = new PortalService();
        testService.setServiceName("PayTest " + System.nanoTime());
        testService.setActive(true);
        testService = serviceRepository.save(testService);

        // Employee users (safe — EMPLOYEE role is not unique-constrained)
        createUser(EMPLOYEE_PHONE, Role.EMPLOYEE);
        createUser(UNASSIGNED_EMP, Role.EMPLOYEE);
        // Customer users
        createUser(CUSTOMER_PHONE, Role.CUSTOMER);
        createUser(OTHER_CUST_PHONE, Role.CUSTOMER);

        // Employee records
        assignedEmployee = createEmployee("Assigned Emp", EMPLOYEE_PHONE);
        unassignedEmployee = createEmployee("Unassigned Emp", UNASSIGNED_EMP);
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private void cleanTestData() {
        // Clean audit logs + tasks + requests belonging to our test customers
        for (CustomerRequest req : requestRepository.findAll()) {
            String phone = req.getPhoneNumber();
            if (CUSTOMER_PHONE.equals(phone) || OTHER_CUST_PHONE.equals(phone)) {
                auditLogRepository.deleteAll(
                        auditLogRepository.findByRequestIdOrderByPerformedAtAsc(req.getId()));
                taskRepository.findAll().stream()
                        .filter(t -> t.getRequest() != null
                                && req.getId().equals(t.getRequest().getId()))
                        .forEach(taskRepository::delete);
                requestRepository.delete(req);
            }
        }

        // Delete test users / employees
        for (String phone : List.of(EMPLOYEE_PHONE, CUSTOMER_PHONE, OTHER_CUST_PHONE, UNASSIGNED_EMP)) {
            Employee e = employeeRepository.findByPhoneNumber(phone);
            if (e != null) employeeRepository.delete(e);
            userRepository.findByPhoneNumber(phone).ifPresent(userRepository::delete);
        }

        // Delete test service if it was saved
        if (testService != null && testService.getId() != null) {
            try { serviceRepository.deleteById(testService.getId()); } catch (Exception ignored) {}
            testService = null;
        }
    }

    private User createUser(String phone, Role role) {
        User u = new User();
        u.setName("Test " + phone);
        u.setPhoneNumber(phone);
        u.setPassword(passwordEncoder.encode("password123"));
        u.setRole(role);
        return userRepository.save(u);
    }

    private Employee createEmployee(String name, String phone) {
        Employee e = new Employee();
        e.setName(name);
        e.setPhoneNumber(phone);
        e.setActive(true);
        return employeeRepository.save(e);
    }

    private CustomerRequest createRequestWithTask(String customerPhone, Employee employee) {
        CustomerRequest req = new CustomerRequest();
        req.setCustomerName("Test Customer");
        req.setPhoneNumber(customerPhone);
        req.setService(testService);
        req.setStatus(RequestStatus.ASSIGNED);
        req.setPaymentStatus(PaymentStatus.UNPAID);
        req.setAmount(0.0);
        req.setCreatedAt(LocalDateTime.now());
        req = requestRepository.saveAndFlush(req);

        Task task = new Task();
        task.setRequest(req);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setPriority(Priority.MEDIUM);
        task.setEmployee(employee);
        taskRepository.save(task);

        return req;
    }

    private String paymentUrl(Long requestId) {
        return "/requests/" + requestId + "/payment";
    }

    /** Authentication token representing OWNER (no DB user required). */
    private Authentication ownerAuth() {
        return new UsernamePasswordAuthenticationToken(
                OWNER_PHONE, null,
                List.of(new SimpleGrantedAuthority("OWNER")));
    }

    /** Authentication token representing the assigned employee. */
    private Authentication assignedEmpAuth() {
        return new UsernamePasswordAuthenticationToken(
                EMPLOYEE_PHONE, null,
                List.of(new SimpleGrantedAuthority("EMPLOYEE")));
    }

    /** Authentication token representing the unassigned employee. */
    private Authentication unassignedEmpAuth() {
        return new UsernamePasswordAuthenticationToken(
                UNASSIGNED_EMP, null,
                List.of(new SimpleGrantedAuthority("EMPLOYEE")));
    }

    // =========================================================
    // TEST CASES
    // =========================================================

    /**
     * Test 1 — CUSTOMER cannot call POST /requests/{id}/payment (403).
     */
    @Test
    @DisplayName("1. CUSTOMER cannot call POST /requests/{id}/payment (403)")
    void test1_CustomerCannotUpdatePayment() throws Exception {
        CustomerRequest req = createRequestWithTask(CUSTOMER_PHONE, assignedEmployee);

        mockMvc.perform(post(paymentUrl(req.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(CUSTOMER_PHONE)
                                .authorities(new SimpleGrantedAuthority("CUSTOMER")))
                        .param("status", "PAID")
                        .param("amount", "500"))
                .andExpect(status().isForbidden());

        CustomerRequest reloaded = requestRepository.findById(req.getId()).orElseThrow();
        assertEquals(PaymentStatus.UNPAID, reloaded.getPaymentStatus());
    }

    /**
     * Test 2 — Customer cannot update another customer's request payment.
     */
    @Test
    @DisplayName("2. CUSTOMER cannot update another customer's request payment")
    void test2_CustomerCannotUpdateOtherCustomerPayment() throws Exception {
        CustomerRequest otherReq = createRequestWithTask(OTHER_CUST_PHONE, assignedEmployee);

        mockMvc.perform(post(paymentUrl(otherReq.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(CUSTOMER_PHONE)
                                .authorities(new SimpleGrantedAuthority("CUSTOMER")))
                        .param("status", "PAID")
                        .param("amount", "300"))
                .andExpect(status().isForbidden());
    }

    /**
     * Test 3 — Unassigned EMPLOYEE cannot update payment of unrelated request.
     */
    @Test
    @DisplayName("3. Unassigned EMPLOYEE cannot update payment of unrelated request")
    void test3_UnassignedEmployeeCannotUpdatePayment() throws Exception {
        // Assigned to assignedEmployee, not unassignedEmployee
        CustomerRequest req = createRequestWithTask(CUSTOMER_PHONE, assignedEmployee);

        mockMvc.perform(post(paymentUrl(req.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(UNASSIGNED_EMP)
                                .authorities(new SimpleGrantedAuthority("EMPLOYEE")))
                        .param("status", "PAID")
                        .param("amount", "200"))
                .andExpect(status().isForbidden());

        CustomerRequest reloaded = requestRepository.findById(req.getId()).orElseThrow();
        assertEquals(PaymentStatus.UNPAID, reloaded.getPaymentStatus());
    }

    /**
     * Test 4 — Amount zero or negative is rejected when marking PAID.
     */
    @Test
    @DisplayName("4. Amount <= 0 is rejected when marking PAID")
    void test4_ZeroOrNegativeAmountRejected() throws Exception {
        CustomerRequest req = createRequestWithTask(CUSTOMER_PHONE, assignedEmployee);

        mockMvc.perform(post(paymentUrl(req.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(EMPLOYEE_PHONE)
                                .authorities(new SimpleGrantedAuthority("EMPLOYEE")))
                        .param("status", "PAID")
                        .param("amount", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(paymentUrl(req.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(EMPLOYEE_PHONE)
                                .authorities(new SimpleGrantedAuthority("EMPLOYEE")))
                        .param("status", "PAID")
                        .param("amount", "-100"))
                .andExpect(status().isBadRequest());

        CustomerRequest reloaded = requestRepository.findById(req.getId()).orElseThrow();
        assertEquals(PaymentStatus.UNPAID, reloaded.getPaymentStatus());
    }

    /**
     * Test 5 — Amount exceeding upper bound (10,000,000) is rejected.
     */
    @Test
    @DisplayName("5. Amount > 10,000,000 is rejected")
    void test5_ExcessiveAmountRejected() throws Exception {
        CustomerRequest req = createRequestWithTask(CUSTOMER_PHONE, assignedEmployee);

        mockMvc.perform(post(paymentUrl(req.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(EMPLOYEE_PHONE)
                                .authorities(new SimpleGrantedAuthority("EMPLOYEE")))
                        .param("status", "PAID")
                        .param("amount", "10000001"))
                .andExpect(status().isBadRequest());

        CustomerRequest reloaded = requestRepository.findById(req.getId()).orElseThrow();
        assertEquals(PaymentStatus.UNPAID, reloaded.getPaymentStatus());
    }

    /**
     * Test 6 — PAID → PAID transition is rejected (duplicate payment guard).
     */
    @Test
    @DisplayName("6. PAID -> PAID transition is rejected (duplicate payment guard)")
    void test6_DuplicatePaymentRejected() throws Exception {
        CustomerRequest req = createRequestWithTask(CUSTOMER_PHONE, assignedEmployee);

        // First payment succeeds
        mockMvc.perform(post(paymentUrl(req.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(OWNER_PHONE)
                                .authorities(new SimpleGrantedAuthority("OWNER")))
                        .param("status", "PAID")
                        .param("amount", "750"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        // Second payment (different amount) must be rejected
        mockMvc.perform(post(paymentUrl(req.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(OWNER_PHONE)
                                .authorities(new SimpleGrantedAuthority("OWNER")))
                        .param("status", "PAID")
                        .param("amount", "999"))
                .andExpect(status().isBadRequest());

        // Amount must not be silently overwritten
        CustomerRequest reloaded = requestRepository.findById(req.getId()).orElseThrow();
        assertEquals(750.0, reloaded.getAmount(), 0.001);
    }

    /**
     * Test 7 — OWNER can mark UNPAID -> PAID; audit log is created.
     */
    @Test
    @DisplayName("7. OWNER can mark UNPAID -> PAID; audit log is created")
    void test7_OwnerCanMarkPaid() throws Exception {
        CustomerRequest req = createRequestWithTask(CUSTOMER_PHONE, assignedEmployee);

        mockMvc.perform(post(paymentUrl(req.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(OWNER_PHONE)
                                .authorities(new SimpleGrantedAuthority("OWNER")))
                        .param("status", "PAID")
                        .param("amount", "1200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.amount").value(1200.0));

        CustomerRequest reloaded = requestRepository.findById(req.getId()).orElseThrow();
        assertEquals(PaymentStatus.PAID, reloaded.getPaymentStatus());
        assertNotNull(reloaded.getPaymentDate());

        List<PaymentAuditLog> logs =
                auditLogRepository.findByRequestIdOrderByPerformedAtAsc(req.getId());
        assertEquals(1, logs.size());
        assertEquals(PaymentStatus.UNPAID, logs.get(0).getPreviousStatus());
        assertEquals(PaymentStatus.PAID,   logs.get(0).getNewStatus());
        assertEquals(1200.0, logs.get(0).getAmount(), 0.001);
        assertEquals(OWNER_PHONE, logs.get(0).getPerformedBy());
    }

    /**
     * Test 8 — Assigned EMPLOYEE can mark UNPAID -> PAID.
     */
    @Test
    @DisplayName("8. Assigned EMPLOYEE can mark UNPAID -> PAID")
    void test8_AssignedEmployeeCanMarkPaid() throws Exception {
        CustomerRequest req = createRequestWithTask(CUSTOMER_PHONE, assignedEmployee);

        mockMvc.perform(post(paymentUrl(req.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(EMPLOYEE_PHONE)
                                .authorities(new SimpleGrantedAuthority("EMPLOYEE")))
                        .param("status", "PAID")
                        .param("amount", "800"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        CustomerRequest reloaded = requestRepository.findById(req.getId()).orElseThrow();
        assertEquals(PaymentStatus.PAID, reloaded.getPaymentStatus());
        assertEquals(800.0, reloaded.getAmount(), 0.001);
    }

    /**
     * Test 9 — OWNER can reverse PAID -> UNPAID; audit log notes admin reversal.
     */
    @Test
    @DisplayName("9. OWNER can reverse PAID -> UNPAID (two audit log entries)")
    void test9_OwnerCanReversePayment() throws Exception {
        CustomerRequest req = createRequestWithTask(CUSTOMER_PHONE, assignedEmployee);

        // Mark paid
        mockMvc.perform(post(paymentUrl(req.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(OWNER_PHONE)
                                .authorities(new SimpleGrantedAuthority("OWNER")))
                        .param("status", "PAID")
                        .param("amount", "500"))
                .andExpect(status().isOk());

        // Reverse to UNPAID
        mockMvc.perform(post(paymentUrl(req.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(OWNER_PHONE)
                                .authorities(new SimpleGrantedAuthority("OWNER")))
                        .param("status", "UNPAID")
                        .param("amount", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));

        CustomerRequest reloaded = requestRepository.findById(req.getId()).orElseThrow();
        assertEquals(PaymentStatus.UNPAID, reloaded.getPaymentStatus());
        assertEquals(0.0, reloaded.getAmount(), 0.001);
        assertNull(reloaded.getPaymentDate());

        List<PaymentAuditLog> logs =
                auditLogRepository.findByRequestIdOrderByPerformedAtAsc(req.getId());
        assertEquals(2, logs.size());
        assertEquals("Admin reversal by OWNER", logs.get(1).getNotes());
    }

    /**
     * Test 9b — EMPLOYEE cannot reverse PAID -> UNPAID.
     */
    @Test
    @DisplayName("9b. EMPLOYEE cannot reverse PAID -> UNPAID (OWNER-only operation)")
    void test9b_EmployeeCannotReversePayment() {
        CustomerRequest req = createRequestWithTask(CUSTOMER_PHONE, assignedEmployee);

        // Mark as PAID directly (service-level, no HTTP)
        req.setPaymentStatus(PaymentStatus.PAID);
        req.setAmount(500.0);
        req.setPaymentDate(LocalDateTime.now());
        requestRepository.saveAndFlush(req);

        // Try to reverse as employee
        org.springframework.security.access.AccessDeniedException ex =
                assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                        requestService.updatePayment(
                                req.getId(), PaymentStatus.UNPAID, 0.0, assignedEmpAuth()));

        assertTrue(ex.getMessage().contains("Only the OWNER"));

        CustomerRequest reloaded = requestRepository.findById(req.getId()).orElseThrow();
        assertEquals(PaymentStatus.PAID, reloaded.getPaymentStatus());
    }

    /**
     * Test 10 — Payment is scoped to the correct request ID; non-existent ID returns 404.
     */
    @Test
    @DisplayName("10. Payment update is scoped to the correct request; non-existent ID returns 404")
    void test10_PaymentScopedToRequest() throws Exception {
        CustomerRequest reqA = createRequestWithTask(CUSTOMER_PHONE, assignedEmployee);
        CustomerRequest reqB = createRequestWithTask(OTHER_CUST_PHONE, assignedEmployee);

        // Mark reqA as paid
        mockMvc.perform(post(paymentUrl(reqA.getId()))
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(OWNER_PHONE)
                                .authorities(new SimpleGrantedAuthority("OWNER")))
                        .param("status", "PAID")
                        .param("amount", "600"))
                .andExpect(status().isOk());

        // reqB must remain UNPAID
        CustomerRequest reloadedB = requestRepository.findById(reqB.getId()).orElseThrow();
        assertEquals(PaymentStatus.UNPAID, reloadedB.getPaymentStatus());

        // Non-existent ID returns 404
        mockMvc.perform(post("/requests/99999999/payment")
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(OWNER_PHONE)
                                .authorities(new SimpleGrantedAuthority("OWNER")))
                        .param("status", "PAID")
                        .param("amount", "100"))
                .andExpect(status().isNotFound());
    }

    /**
     * Test 11 — Receipt upload by unassigned employee is denied.
     */
    @Test
    @DisplayName("11. Receipt upload by unassigned employee is denied (403)")
    void test11_ReceiptUploadUnassignedEmployeeDenied() throws Exception {
        CustomerRequest req = createRequestWithTask(CUSTOMER_PHONE, assignedEmployee);
        Long taskId = taskRepository.findAll().stream()
                .filter(t -> t.getRequest() != null && t.getRequest().getId().equals(req.getId()))
                .findFirst().orElseThrow().getId();

        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "receipt.pdf", "application/pdf",
                        "dummy-pdf-content".getBytes());

        mockMvc.perform(multipart("/receipts/" + taskId + "/upload")
                        .file(file)
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(UNASSIGNED_EMP)
                                .authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Test 12 — Concurrent payment updates cannot corrupt payment state.
     * 10 threads race to mark the same UNPAID request as PAID.
     * Exactly one must succeed; the others must fail safely.
     */
    @Test
    @DisplayName("12. Concurrent payment updates produce exactly one PAID record")
    void test12_ConcurrentPaymentUpdates() throws Exception {
        CustomerRequest req = createRequestWithTask(CUSTOMER_PHONE, assignedEmployee);
        Long requestId = req.getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate  = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger safeFailCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final double amt = 100.0 + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    requestService.updatePayment(requestId, PaymentStatus.PAID, amt, assignedEmpAuth());
                    successCount.incrementAndGet();
                } catch (com.eservice1.common.exception.InvalidOperationException e) {
                    // "already paid" guard fired — safe
                    safeFailCount.incrementAndGet();
                } catch (jakarta.persistence.OptimisticLockException
                         | org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                    // Optimistic lock conflict — safe
                    safeFailCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(finishGate.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successCount.get(),
                "Exactly one concurrent payment update must succeed");
        assertEquals(threadCount - 1, safeFailCount.get(),
                "All other attempts must fail safely");
        assertTrue(unexpectedErrors.isEmpty(),
                "Unexpected errors: " + unexpectedErrors);

        // DB: exactly PAID, positive amount
        CustomerRequest reloaded = requestRepository.findById(requestId).orElseThrow();
        assertEquals(PaymentStatus.PAID, reloaded.getPaymentStatus());
        assertTrue(reloaded.getAmount() > 0);

        // Exactly one audit log entry
        List<PaymentAuditLog> logs =
                auditLogRepository.findByRequestIdOrderByPerformedAtAsc(requestId);
        assertEquals(1, logs.size(), "Only one audit log entry must exist");
    }
}
