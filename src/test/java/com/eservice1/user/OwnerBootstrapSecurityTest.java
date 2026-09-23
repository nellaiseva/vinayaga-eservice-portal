package com.eservice1.user;

import com.eservice1.common.Role;
import com.eservice1.common.exception.DuplicateResourceException;
import com.eservice1.user.dto.LoginRequest;
import com.eservice1.user.dto.RegisterRequest;
import com.eservice1.user.entity.User;
import com.eservice1.user.repository.UserRepository;
import com.eservice1.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class OwnerBootstrapSecurityTest {

    private static final String TEST_BOOTSTRAP_TOKEN = "test-secure-bootstrap-token-999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User originalOwner;

    @BeforeEach
    void setUp() {
        userService.setOwnerBootstrapToken(TEST_BOOTSTRAP_TOKEN);

        // Ensure database index uk_users_single_owner exists
        try {
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uk_users_single_owner ON users (role) WHERE role = 'OWNER'"
            );
        } catch (Exception ignored) {}

        // Backup existing owner if any
        Optional<User> ownerOpt = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.OWNER)
                .findFirst();
        originalOwner = ownerOpt.orElse(null);
    }

    @AfterEach
    void tearDown() {
        // Reset bootstrap token to default disabled state
        userService.setOwnerBootstrapToken(null);

        // Clean up test non-owner users
        List<User> usersToDelete = userRepository.findAll().stream()
                .filter(u -> u.getPhoneNumber() != null && u.getPhoneNumber().startsWith("99999999"))
                .toList();
        userRepository.deleteAll(usersToDelete);

        // Ensure exactly one owner remains intact
        long currentOwnerCount = userRepository.countByRole(Role.OWNER);
        if (currentOwnerCount == 0 && originalOwner != null) {
            User restored = new User();
            restored.setName(originalOwner.getName());
            restored.setPhoneNumber(originalOwner.getPhoneNumber());
            restored.setPassword(originalOwner.getPassword());
            restored.setRole(Role.OWNER);
            userRepository.save(restored);
        } else if (currentOwnerCount > 1) {
            List<User> owners = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == Role.OWNER)
                    .toList();
            for (int i = 1; i < owners.size(); i++) {
                userRepository.delete(owners.get(i));
            }
        }
    }

    private void ensureExistingOwner(String phone, String password) {
        // Delete all owners first
        List<User> existingOwners = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.OWNER)
                .toList();
        userRepository.deleteAll(existingOwners);

        User owner = new User();
        owner.setName("Primary Owner");
        owner.setPhoneNumber(phone);
        owner.setPassword(passwordEncoder.encode(password));
        owner.setRole(Role.OWNER);
        userRepository.saveAndFlush(owner);
    }

    private void clearAllOwners() {
        List<User> existingOwners = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.OWNER)
                .toList();
        userRepository.deleteAll(existingOwners);
        userRepository.flush();
    }

    @Test
    @DisplayName("1. Normal unauthenticated request cannot create an OWNER in production mode (missing token -> 403)")
    void testUnauthenticatedRequestCannotCreateOwnerWithoutToken() throws Exception {
        // Set bootstrap token to null (production default)
        userService.setOwnerBootstrapToken(null);

        RegisterRequest request = new RegisterRequest();
        request.setName("Attacker");
        request.setPhoneNumber("9999999901");
        request.setPassword("attackerPassword123");

        mockMvc.perform(post("/auth/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("1b. Unauthenticated request with invalid bootstrap token returns 403")
    void testRequestWithInvalidTokenReturnsForbidden() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("Attacker");
        request.setPhoneNumber("9999999902");
        request.setPassword("attackerPassword123");

        mockMvc.perform(post("/auth/owner")
                        .header("X-Bootstrap-Token", "wrong-invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2. Request containing role=OWNER on /auth/register cannot bypass protection")
    @WithMockUser(authorities = "OWNER")
    void testRegisterWithRoleOwnerRejected() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("Privilege Escalation");
        request.setPhoneNumber("9999999903");
        request.setPassword("escalatePassword123");
        request.setRole(Role.OWNER);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("2b. Register with role=null defaults safely to CUSTOMER")
    @WithMockUser(authorities = "OWNER")
    void testRegisterWithNullRoleDefaultsToCustomer() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("Default Customer");
        request.setPhoneNumber("9999999904");
        request.setPassword("customerPassword123");
        request.setRole(null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        Optional<User> saved = userRepository.findByPhoneNumber("9999999904");
        assertTrue(saved.isPresent());
        assertEquals(Role.CUSTOMER, saved.get().getRole());
    }

    @Test
    @DisplayName("3. Existing OWNER cannot be replaced or duplicated through bootstrap (returns 409 Conflict)")
    void testExistingOwnerCannotBeDuplicatedThroughBootstrap() throws Exception {
        ensureExistingOwner("9999999905", "existingOwnerPass123");

        RegisterRequest request = new RegisterRequest();
        request.setName("Duplicate Owner");
        request.setPhoneNumber("9999999906");
        request.setPassword("duplicatePassword123");

        mockMvc.perform(post("/auth/owner")
                        .header("X-Bootstrap-Token", TEST_BOOTSTRAP_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("4. Existing CUSTOMER cannot become OWNER through request manipulation")
    @WithMockUser(authorities = "CUSTOMER")
    void testCustomerCannotElevateToOwner() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("Customer Attacker");
        request.setPhoneNumber("9999999907");
        request.setPassword("password123");
        request.setRole(Role.OWNER);

        // /auth/register requires OWNER authority -> customer gets 403 Forbidden
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. Existing EMPLOYEE cannot become OWNER through request manipulation")
    @WithMockUser(authorities = "EMPLOYEE")
    void testEmployeeCannotElevateToOwner() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("Employee Attacker");
        request.setPhoneNumber("9999999908");
        request.setPassword("password123");
        request.setRole(Role.OWNER);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6. Two concurrent bootstrap attempts cannot create two OWNER accounts (Actual Multi-Threaded Execution)")
    void testConcurrentBootstrapAttemptsCannotCreateTwoOwners() throws Exception {
        clearAllOwners();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    // Wait for start signal so all threads invoke simultaneously
                    startGate.await();

                    RegisterRequest req = new RegisterRequest();
                    req.setName("Concurrent Owner " + index);
                    req.setPhoneNumber("999999991" + index);
                    req.setPassword("concurrentPass" + index);

                    userService.createOwner(req, TEST_BOOTSTRAP_TOKEN);
                    successCount.incrementAndGet();
                } catch (DuplicateResourceException e) {
                    conflictCount.incrementAndGet();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    finishGate.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startGate.countDown();

        boolean finished = finishGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(finished, "Concurrent bootstrap tasks should finish within 10 seconds");

        // Exactly one thread must succeed, and the other must be rejected
        assertEquals(1, successCount.get(), "Exactly one concurrent bootstrap request must succeed");
        assertEquals(1, conflictCount.get(), "The losing concurrent request must encounter a duplicate resource conflict");
        assertTrue(exceptions.isEmpty(), "Unexpected exceptions occurred: " + exceptions);

        // Database invariant check
        long finalOwnerCount = userRepository.countByRole(Role.OWNER);
        assertEquals(1, finalOwnerCount, "Database must contain exactly 1 owner after concurrent bootstrap attempts");
    }

    @Test
    @DisplayName("7. Database ultimately contains at most one OWNER (Storage Invariant Enforced)")
    void testDatabaseContainsAtMostOneOwner() {
        ensureExistingOwner("9999999920", "ownerPass123");

        long ownerCount = userRepository.countByRole(Role.OWNER);
        assertEquals(1, ownerCount, "Database must contain exactly 1 owner");

        // Test that direct SQL insertion of a second owner is blocked by PostgreSQL partial unique index
        assertThrows(Exception.class, () -> {
            jdbcTemplate.execute(
                    "INSERT INTO users (name, phone_number, password, role) " +
                            "VALUES ('Second Owner', '9999999921', 'pwd', 'OWNER')"
            );
        }, "Database unique index uk_users_single_owner must reject inserting a second OWNER");
    }

    @Test
    @DisplayName("8. Existing normal login still works (EMPLOYEE login)")
    void testNormalEmployeeLoginWorks() throws Exception {
        // Create an employee user
        User employee = new User();
        employee.setName("Test Employee");
        employee.setPhoneNumber("9999999930");
        employee.setPassword(passwordEncoder.encode("employeePass123!"));
        employee.setRole(Role.EMPLOYEE);
        userRepository.saveAndFlush(employee);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setPhoneNumber("9999999930");
        loginRequest.setPassword("employeePass123!");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    @DisplayName("9. Existing OWNER login still works")
    void testOwnerLoginWorks() throws Exception {
        ensureExistingOwner("9999999940", "ownerSecretPass123!");

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setPhoneNumber("9999999940");
        loginRequest.setPassword("ownerSecretPass123!");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.role").value("OWNER"));
    }
}
