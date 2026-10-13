package com.eservice1.customer;

import com.eservice1.common.Role;
import com.eservice1.customer.dto.CustomerPasswordResetRequest;
import com.eservice1.customer.dto.EnrollCustomerRequest;
import com.eservice1.customer.dto.OtpResponse;
import com.eservice1.customer.entity.OtpPurpose;
import com.eservice1.customer.entity.OtpVerification;
import com.eservice1.customer.repository.OtpVerificationRepository;
import com.eservice1.customer.service.OtpService;
import com.eservice1.user.entity.User;
import com.eservice1.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL Integration Test for Customer Enrollment and Password Reset Concurrency.
 *
 * Verifies real PostgreSQL transactional advisory locks (pg_advisory_xact_lock):
 * - Multiple concurrent threads executing separate database transactions.
 * - PostgreSQL serializes concurrent requests for the same phone number.
 * - Exactly one concurrent operation succeeds; the second operation observes
 *   the committed state of the first and is safely rejected.
 */
@SpringBootTest
public class CustomerAuthConcurrencyIntegrationTest {

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String ENROLL_PHONE = "7777777111";
    private static final String RESET_PHONE  = "7777777222";

    @BeforeEach
    @AfterEach
    void cleanData() {
        for (String phone : List.of(ENROLL_PHONE, RESET_PHONE)) {
            otpRepository.findAll().stream()
                    .filter(o -> phone.equals(o.getPhoneNumber()))
                    .forEach(otpRepository::delete);
            userRepository.findByPhoneNumber(phone).ifPresent(userRepository::delete);
        }
    }

    @Test
    @DisplayName("PostgreSQL integration: Concurrent enrollment across separate transactions allows exactly one success")
    void testConcurrentEnrollment_PostgreSQLAdvisoryLock() throws Exception {
        String plainOtp = "123456";

        // Seed a single valid enrollment OTP in PostgreSQL
        OtpVerification otp = new OtpVerification();
        otp.setPhoneNumber(ENROLL_PHONE);
        otp.setPurpose(OtpPurpose.CUSTOMER_ENROLLMENT);
        otp.setOtp(passwordEncoder.encode(plainOtp));
        otp.setCreatedAt(Instant.now());
        otp.setExpiresAt(Instant.now().plusSeconds(300));
        otp.setVerified(false);
        otp.setAttempts(0);
        otpRepository.save(otp);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        Callable<OtpResponse> task = () -> {
            barrier.await();
            return otpService.completeCustomerEnrollment(
                    new EnrollCustomerRequest(ENROLL_PHONE, plainOtp, "1234")
            );
        };

        Future<OtpResponse> future1 = executor.submit(task);
        Future<OtpResponse> future2 = executor.submit(task);

        OtpResponse res1 = future1.get(10, TimeUnit.SECONDS);
        OtpResponse res2 = future2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        int successCount = (res1.isSuccess() ? 1 : 0) + (res2.isSuccess() ? 1 : 0);
        assertEquals(1, successCount,
                "Real PostgreSQL advisory locks must ensure exactly one concurrent enrollment succeeds");

        // Verify database state after transactions finish
        User user = userRepository.findByPhoneNumber(ENROLL_PHONE).orElse(null);
        assertNotNull(user);
        assertTrue(user.isCustomerEnrolled());
        assertTrue(passwordEncoder.matches("1234", user.getPassword()));

        // OTP must be deleted
        List<OtpVerification> remaining = otpRepository.findAll().stream()
                .filter(o -> ENROLL_PHONE.equals(o.getPhoneNumber()))
                .toList();
        assertTrue(remaining.isEmpty(), "Enrollment OTP must be deleted upon consumption");
    }

    @Test
    @DisplayName("PostgreSQL integration: Concurrent password reset across separate transactions allows exactly one success")
    void testConcurrentPasswordReset_PostgreSQLAdvisoryLock() throws Exception {
        String plainOtp = "654321";

        // Seed an enrolled customer
        User user = new User();
        user.setName("Test Citizen");
        user.setPhoneNumber(RESET_PHONE);
        user.setRole(Role.CUSTOMER);
        user.setCustomerEnrolled(true);
        user.setPassword(passwordEncoder.encode("OldPassword123"));
        userRepository.save(user);

        // Seed a valid reset OTP in PostgreSQL
        OtpVerification otp = new OtpVerification();
        otp.setPhoneNumber(RESET_PHONE);
        otp.setPurpose(OtpPurpose.CUSTOMER_PASSWORD_RESET);
        otp.setOtp(passwordEncoder.encode(plainOtp));
        otp.setCreatedAt(Instant.now());
        otp.setExpiresAt(Instant.now().plusSeconds(300));
        otp.setVerified(false);
        otp.setAttempts(0);
        otpRepository.save(otp);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        Callable<OtpResponse> task = () -> {
            barrier.await();
            return otpService.resetCustomerPassword(
                    new CustomerPasswordResetRequest(RESET_PHONE, plainOtp, "asdd")
            );
        };

        Future<OtpResponse> future1 = executor.submit(task);
        Future<OtpResponse> future2 = executor.submit(task);

        OtpResponse res1 = future1.get(10, TimeUnit.SECONDS);
        OtpResponse res2 = future2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        int successCount = (res1.isSuccess() ? 1 : 0) + (res2.isSuccess() ? 1 : 0);
        assertEquals(1, successCount,
                "Real PostgreSQL advisory locks must ensure exactly one concurrent reset succeeds");

        // Verify password was updated to 'asdd'
        User updated = userRepository.findByPhoneNumber(RESET_PHONE).orElse(null);
        assertNotNull(updated);
        assertTrue(passwordEncoder.matches("asdd", updated.getPassword()));

        // OTP must be deleted
        List<OtpVerification> remaining = otpRepository.findAll().stream()
                .filter(o -> RESET_PHONE.equals(o.getPhoneNumber()))
                .toList();
        assertTrue(remaining.isEmpty(), "Reset OTP must be deleted upon consumption");
    }
}
