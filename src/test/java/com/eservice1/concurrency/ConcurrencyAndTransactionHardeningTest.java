package com.eservice1.concurrency;

import com.eservice1.common.Role;
import org.springframework.security.access.AccessDeniedException;
import com.eservice1.common.exception.DuplicateResourceException;
import com.eservice1.common.exception.InvalidOperationException;
import com.eservice1.customer.entity.CustomerProfile;
import com.eservice1.customer.repository.CustomerProfileRepository;
import com.eservice1.employee.entity.Employee;
import com.eservice1.employee.entity.Priority;
import com.eservice1.employee.entity.Receipt;
import com.eservice1.employee.entity.Task;
import com.eservice1.employee.entity.TaskStatus;
import com.eservice1.employee.repository.EmployeeRepository;
import com.eservice1.employee.repository.ReceiptRepository;
import com.eservice1.employee.repository.TaskRepository;
import com.eservice1.employee.service.ReceiptService;
import com.eservice1.employee.service.TaskService;
import com.eservice1.service.entity.PortalService;
import com.eservice1.service.repository.PortalServiceRepository;
import com.eservice1.submission.dto.CustomerRequestDTO;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.entity.PaymentStatus;
import com.eservice1.submission.entity.RequestStatus;
import com.eservice1.submission.repository.CustomerRequestRepository;
import com.eservice1.submission.repository.UploadedDocumentRepository;
import com.eservice1.submission.service.CustomerRequestService;
import com.eservice1.user.entity.User;
import com.eservice1.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 — Concurrency &amp; Transaction Hardening Verification Suite.
 *
 * Verifies:
 * 1. CustomerRequest creation rollback atomicity (Request + Task).
 * 2. Concurrent task acceptance authorization and determinism.
 * 3. Concurrent task assignment determinism without lost updates or duplicate ownership.
 * 4. Request status concurrency ensuring state transition validity.
 * 5. Employee ownership remaining strictly enforced under concurrency.
 * 6. Concurrent mutation producing no lost state (e.g. concurrent completeTask).
 * 7. Optimistic locking and data integrity conflicts returning HTTP 409 Conflict.
 * 8. File and database atomicity compensation cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class ConcurrencyAndTransactionHardeningTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CustomerRequestService requestService;
    @Autowired private CustomerRequestRepository requestRepository;
    @SpyBean   private TaskRepository taskRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerProfileRepository customerProfileRepository;
    @Autowired private PortalServiceRepository serviceRepository;
    @Autowired private TaskService taskService;
    @Autowired private ReceiptService receiptService;
    @SpyBean   private ReceiptRepository receiptRepository;
    @SpyBean   private UploadedDocumentRepository uploadedDocumentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // Distinct phone numbers with 7777777 prefix to avoid collision
    private static final String OWNER_PHONE      = "7777777001";
    private static final String EMP_A_PHONE      = "7777777002";
    private static final String EMP_B_PHONE      = "7777777003";
    private static final String CUSTOMER_PHONE   = "7777777004";
    private static final String OTHER_CUST_PHONE = "7777777005";

    private Employee employeeA;
    private Employee employeeB;
    private PortalService testService;
    private CustomerProfile customerProfile;

    @BeforeEach
    void setUp() {
        cleanTestData();
        reset(taskRepository, receiptRepository, uploadedDocumentRepository);

        // Create PortalService
        testService = new PortalService();
        testService.setServiceName("ConcurrencyService " + System.nanoTime());
        testService.setActive(true);
        testService = serviceRepository.save(testService);

        // Create User accounts
        createUser(EMP_A_PHONE, Role.EMPLOYEE);
        createUser(EMP_B_PHONE, Role.EMPLOYEE);
        createUser(CUSTOMER_PHONE, Role.CUSTOMER);
        createUser(OTHER_CUST_PHONE, Role.CUSTOMER);

        // Create Employee profiles
        employeeA = createEmployee("Worker A", EMP_A_PHONE);
        employeeB = createEmployee("Worker B", EMP_B_PHONE);

        // Create Customer profile
        customerProfile = new CustomerProfile();
        customerProfile.setPhoneNumber(CUSTOMER_PHONE);
        customerProfile.setCustomerName("Concurrency Customer");
        customerProfile.setDob("1995-05-15");
        customerProfile = customerProfileRepository.save(customerProfile);
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
        reset(taskRepository, receiptRepository, uploadedDocumentRepository);
    }

    private void cleanTestData() {
        // Clean customer requests, tasks, and receipts
        for (CustomerRequest req : requestRepository.findAll()) {
            String p = req.getPhoneNumber();
            if (CUSTOMER_PHONE.equals(p) || OTHER_CUST_PHONE.equals(p)) {
                taskRepository.findAll().stream()
                        .filter(t -> t.getRequest() != null && req.getId().equals(t.getRequest().getId()))
                        .forEach(t -> {
                            receiptRepository.findAll().stream()
                                    .filter(r -> r.getTask() != null && t.getId().equals(r.getTask().getId()))
                                    .forEach(receiptRepository::delete);
                            taskRepository.delete(t);
                        });
                requestRepository.delete(req);
            }
        }

        // Delete users & employees
        for (String phone : List.of(EMP_A_PHONE, EMP_B_PHONE, CUSTOMER_PHONE, OTHER_CUST_PHONE)) {
            Employee e = employeeRepository.findByPhoneNumber(phone);
            if (e != null) employeeRepository.delete(e);
            userRepository.findByPhoneNumber(phone).ifPresent(userRepository::delete);
            CustomerProfile cp = customerProfileRepository.findByPhoneNumber(phone);
            if (cp != null) customerProfileRepository.delete(cp);
        }

        if (testService != null && testService.getId() != null) {
            try { serviceRepository.deleteById(testService.getId()); } catch (Exception ignored) {}
            testService = null;
        }
    }

    private User createUser(String phone, Role role) {
        User u = new User();
        u.setName("User " + phone);
        u.setPhoneNumber(phone);
        u.setPassword(passwordEncoder.encode("Password@123"));
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

    private Authentication authUser(String phone, String role) {
        return new UsernamePasswordAuthenticationToken(
                phone, "N/A", List.of(new SimpleGrantedAuthority(role)));
    }

    private CustomerRequest createTestRequest(Employee assignedEmp) {
        CustomerRequest req = new CustomerRequest();
        req.setCustomerName("Test Request Customer");
        req.setPhoneNumber(CUSTOMER_PHONE);
        req.setService(testService);
        req.setStatus(assignedEmp != null ? RequestStatus.ASSIGNED : RequestStatus.PENDING);
        req.setPaymentStatus(PaymentStatus.UNPAID);
        req.setAmount(0.0);
        req.setCreatedAt(LocalDateTime.now());
        req = requestRepository.saveAndFlush(req);

        Task task = new Task();
        task.setRequest(req);
        task.setStatus(assignedEmp != null ? TaskStatus.ACCEPTED : TaskStatus.PENDING);
        task.setPriority(Priority.MEDIUM);
        task.setEmployee(assignedEmp);
        taskRepository.save(task);

        return req;
    }

    // =========================================================================
    // 1. CustomerRequest Creation Rollback Test
    // =========================================================================
    @Test
    @DisplayName("1. CustomerRequest creation rolls back entirely if Task save fails")
    void test1_CustomerRequestCreationRollback() {
        CustomerRequestDTO dto = new CustomerRequestDTO();
        dto.setCustomerName("Concurrency Customer");
        dto.setPhoneNumber(CUSTOMER_PHONE);
        dto.setServiceId(testService.getId());

        long beforeCount = requestRepository.count();

        // Simulate database failure when saving Task during createRequest
        doThrow(new RuntimeException("Simulated Task DB Persistence Failure"))
                .when(taskRepository).save(any(Task.class));

        assertThrows(RuntimeException.class, () ->
                requestService.createRequest(dto, CUSTOMER_PHONE)
        );

        // Verify complete rollback: no orphan CustomerRequest persisted in DB
        long afterCount = requestRepository.count();
        assertEquals(beforeCount, afterCount,
                "CustomerRequest must be rolled back atomically when Task creation/save fails");

        // Reset spy and verify normal creation works atomically
        reset(taskRepository);
        CustomerRequest created = requestService.createRequest(dto, CUSTOMER_PHONE);
        assertNotNull(created.getId());
        assertTrue(requestRepository.findById(created.getId()).isPresent());
        assertNotNull(taskRepository.findByRequestId(created.getId()));
    }

    // =========================================================================
    // 2. Concurrent Task Acceptance Test
    // =========================================================================
    @Test
    @DisplayName("2. Concurrent task acceptance: owner/assigned employee only, deterministic final state")
    void test2_ConcurrentTaskAcceptance() throws Exception {
        CustomerRequest req = createTestRequest(employeeA);
        Task task = taskRepository.findByRequestId(req.getId());
        Long taskId = task.getId();

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger allowedSuccess = new AtomicInteger(0);
        AtomicInteger deniedForbidden = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final boolean isEmpA = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Authentication auth = isEmpA
                            ? authUser(EMP_A_PHONE, "EMPLOYEE")
                            : authUser(EMP_B_PHONE, "EMPLOYEE");
                    taskService.acceptTask(taskId, auth);
                    allowedSuccess.incrementAndGet();
                } catch (AccessDeniedException e) {
                    deniedForbidden.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(unexpectedErrors.isEmpty(), "Unexpected errors: " + unexpectedErrors);
        assertEquals(5, deniedForbidden.get(), "All 5 attempts by Employee B must be denied");
        assertTrue(allowedSuccess.get() >= 1, "At least one attempt by Employee A must succeed");

        Task updatedTask = taskRepository.findById(taskId).orElseThrow();
        assertEquals(TaskStatus.IN_PROGRESS, updatedTask.getStatus());
        assertEquals(employeeA.getId(), updatedTask.getEmployee().getId());
    }

    // =========================================================================
    // 3. Concurrent Task Assignment Test
    // =========================================================================
    @Test
    @DisplayName("3. Concurrent task assignment: exactly one claimant succeeds, no duplicate ownership")
    void test3_ConcurrentTaskAssignment() throws Exception {
        // Create an unassigned task
        CustomerRequest req = createTestRequest(null);
        Long requestId = req.getId();

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger assignedA = new AtomicInteger(0);
        AtomicInteger assignedB = new AtomicInteger(0);
        AtomicInteger safeRejections = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final boolean tryA = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (tryA) {
                        taskService.selfAssign(requestId, EMP_A_PHONE);
                        assignedA.incrementAndGet();
                    } else {
                        taskService.selfAssign(requestId, EMP_B_PHONE);
                        assignedB.incrementAndGet();
                    }
                } catch (InvalidOperationException e) {
                    // Task already assigned guard fired
                    safeRejections.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(unexpectedErrors.isEmpty(), "Unexpected errors: " + unexpectedErrors);
        assertEquals(1, assignedA.get() + assignedB.get(),
                "Exactly ONE employee must successfully claim the unassigned task");
        assertEquals(threads - 1, safeRejections.get(),
                "All competing claimants must be safely rejected with InvalidOperationException");

        Task finalTask = taskRepository.findByRequestId(requestId);
        assertNotNull(finalTask.getEmployee());
        assertEquals(TaskStatus.ACCEPTED, finalTask.getStatus());
        assertEquals(RequestStatus.IN_PROGRESS, finalTask.getRequest().getStatus());
    }

    // =========================================================================
    // 4. Concurrent Request Status Transition Test
    // =========================================================================
    @Test
    @DisplayName("4. Concurrent status transition: completed task cannot be re-accepted under race condition")
    void test4_ConcurrentRequestStatusTransition() throws Exception {
        CustomerRequest req = createTestRequest(employeeA);
        Task task = taskRepository.findByRequestId(req.getId());
        Long taskId = task.getId();

        // Put task into IN_PROGRESS
        taskService.acceptTask(taskId, authUser(EMP_A_PHONE, "EMPLOYEE"));

        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger completeSuccess = new AtomicInteger(0);
        AtomicInteger acceptRejections = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final boolean doComplete = (i == 0); // one thread completes, others try to re-accept
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (doComplete) {
                        taskService.completeTask(taskId, authUser(EMP_A_PHONE, "EMPLOYEE"));
                        completeSuccess.incrementAndGet();
                    } else {
                        // Sleep slightly to race against or right after completion
                        Thread.sleep(10);
                        taskService.acceptTask(taskId, authUser(EMP_A_PHONE, "EMPLOYEE"));
                    }
                } catch (InvalidOperationException e) {
                    acceptRejections.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(unexpectedErrors.isEmpty(), "Unexpected errors: " + unexpectedErrors);
        assertEquals(1, completeSuccess.get(), "Task completion must succeed");

        Task finalTask = taskRepository.findById(taskId).orElseThrow();
        assertEquals(TaskStatus.COMPLETED, finalTask.getStatus(),
                "Task status must remain COMPLETED and cannot be regressed to IN_PROGRESS");
        assertEquals(RequestStatus.COMPLETED, finalTask.getRequest().getStatus());
    }

    // =========================================================================
    // 5. Employee Ownership Remains Enforced Under Concurrency
    // =========================================================================
    @Test
    @DisplayName("5. Concurrency cannot bypass IDOR: Employee B operations on Employee A task all fail with 403")
    void test5_EmployeeOwnershipEnforcedUnderConcurrency() throws Exception {
        CustomerRequest req = createTestRequest(employeeA);
        Task task = taskRepository.findByRequestId(req.getId());
        Long taskId = task.getId();

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger deniedCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final int action = i % 3;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Authentication empBAuth = authUser(EMP_B_PHONE, "EMPLOYEE");
                    if (action == 0) {
                        taskService.acceptTask(taskId, empBAuth);
                    } else if (action == 1) {
                        taskService.updatePriority(taskId, Priority.HIGH, empBAuth);
                    } else {
                        taskService.completeTask(taskId, empBAuth);
                    }
                } catch (AccessDeniedException e) {
                    deniedCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(unexpectedErrors.isEmpty(), "Unexpected errors: " + unexpectedErrors);
        assertEquals(threads, deniedCount.get(),
                "Every single concurrent attempt by unassigned Employee B must be denied");

        Task finalTask = taskRepository.findById(taskId).orElseThrow();
        assertEquals(employeeA.getId(), finalTask.getEmployee().getId(),
                "Employee A ownership must remain completely intact");
    }

    // =========================================================================
    // 6. Concurrent Mutation Produces No Lost/Invalid State
    // =========================================================================
    @Test
    @DisplayName("6. 10 concurrent completion calls produce exactly 1 success and 9 safe rejections")
    void test6_ConcurrentMutationNoLostState() throws Exception {
        CustomerRequest req = createTestRequest(employeeA);
        Task task = taskRepository.findByRequestId(req.getId());
        Long taskId = task.getId();

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger safeRejectionCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    taskService.completeTask(taskId, authUser(EMP_A_PHONE, "EMPLOYEE"));
                    successCount.incrementAndGet();
                } catch (InvalidOperationException e) {
                    // "already completed" guard fired
                    safeRejectionCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(unexpectedErrors.isEmpty(), "Unexpected errors: " + unexpectedErrors);
        assertEquals(1, successCount.get(), "Exactly one completion must succeed");
        assertEquals(threads - 1, safeRejectionCount.get(),
                "All other attempts must be safely rejected because task is already completed");

        Task reloaded = taskRepository.findById(taskId).orElseThrow();
        assertEquals(TaskStatus.COMPLETED, reloaded.getStatus());
        assertEquals(RequestStatus.COMPLETED, reloaded.getRequest().getStatus());
    }

    // =========================================================================
    // 7. Optimistic Locking Conflict Returns Correct HTTP Response (409)
    // =========================================================================
    @Test
    @DisplayName("7. Optimistic locking and data integrity conflicts return HTTP 409 Conflict")
    void test7_OptimisticLockingReturnsHttpConflict409() throws Exception {
        // Direct verification of GlobalExceptionHandler for ObjectOptimisticLockingFailureException
        com.eservice1.common.exception.GlobalExceptionHandler handler =
                new com.eservice1.common.exception.GlobalExceptionHandler();

        org.springframework.http.ResponseEntity<?> response =
                handler.handleSpringOptimisticLock(
                        new ObjectOptimisticLockingFailureException(CustomerRequest.class, 1L));

        assertEquals(409, response.getStatusCode().value(),
                "ObjectOptimisticLockingFailureException must return HTTP 409 Conflict");
        assertNotNull(response.getBody());

        // Also verify DataIntegrityViolationException returns HTTP 409 Conflict
        org.springframework.http.ResponseEntity<?> diResponse =
                handler.handleDataIntegrityViolation(
                        new DataIntegrityViolationException("Duplicate key violation"));

        assertEquals(409, diResponse.getStatusCode().value(),
                "DataIntegrityViolationException must return HTTP 409 Conflict");
    }

    // =========================================================================
    // 8. File and Database Failure Cleanup Test
    // =========================================================================
    @Test
    @DisplayName("8. File upload compensation deletes physical file if database transaction fails")
    void test8_FileDatabaseFailureCleanup() throws Exception {
        CustomerRequest req = createTestRequest(employeeA);
        Task task = taskRepository.findByRequestId(req.getId());
        Long taskId = task.getId();

        // 1. Test ReceiptService file cleanup on DB failure
        MockMultipartFile receiptFile = new MockMultipartFile(
                "file", "test_receipt.pdf", "application/pdf",
                "test-pdf-receipt-content-bytes".getBytes());

        // Simulate DB failure when saving Receipt
        doThrow(new DataIntegrityViolationException("Simulated DB Receipt Save Failure"))
                .when(receiptRepository).save(any(Receipt.class));

        Path receiptsDir = Path.of("receipts").toAbsolutePath().normalize();
        long filesBeforeReceipt = Files.exists(receiptsDir)
                ? Files.list(receiptsDir).count()
                : 0;

        assertThrows(DataIntegrityViolationException.class, () ->
                receiptService.uploadReceipt(taskId, receiptFile, authUser(EMP_A_PHONE, "EMPLOYEE"))
        );

        long filesAfterReceipt = Files.exists(receiptsDir)
                ? Files.list(receiptsDir).count()
                : 0;
        assertEquals(filesBeforeReceipt, filesAfterReceipt,
                "Physical receipt file must be deleted by compensation cleanup when DB save fails");

        // 2. Test TaskService uploadResult file cleanup on DB failure
        MockMultipartFile resultFile = new MockMultipartFile(
                "file", "result_doc.pdf", "application/pdf",
                "test-result-document-content".getBytes());

        doThrow(new DataIntegrityViolationException("Simulated DB Document Save Failure"))
                .when(uploadedDocumentRepository).save(any());

        Path uploadsDir = Path.of(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();
        long filesBeforeUpload = Files.exists(uploadsDir)
                ? Files.list(uploadsDir).count()
                : 0;

        assertThrows(DataIntegrityViolationException.class, () ->
                taskService.uploadResult(taskId, resultFile, authUser(EMP_A_PHONE, "EMPLOYEE"))
        );

        long filesAfterUpload = Files.exists(uploadsDir)
                ? Files.list(uploadsDir).count()
                : 0;
        assertEquals(filesBeforeUpload, filesAfterUpload,
                "Physical result file must be deleted by compensation cleanup when DB save fails");
    }
}
