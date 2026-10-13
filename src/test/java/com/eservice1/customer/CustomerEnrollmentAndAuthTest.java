package com.eservice1.customer;

import com.eservice1.common.Role;
import com.eservice1.common.exception.AccountNotEnrolledException;
import com.eservice1.common.exception.InvalidCredentialsException;
import com.eservice1.config.JwtService;
import com.eservice1.customer.dto.*;
import com.eservice1.customer.entity.OtpPurpose;
import com.eservice1.customer.entity.OtpVerification;
import com.eservice1.customer.repository.OtpVerificationRepository;
import com.eservice1.customer.service.Msg91Service;
import com.eservice1.customer.service.OtpService;
import com.eservice1.employee.entity.Employee;
import com.eservice1.employee.repository.EmployeeRepository;
import com.eservice1.security.service.LoginAttemptService;
import com.eservice1.user.dto.AuthResponse;
import com.eservice1.user.dto.LoginRequest;
import com.eservice1.user.entity.User;
import com.eservice1.user.repository.UserRepository;
import com.eservice1.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomerEnrollmentAndAuthTest {

    @Mock
    private OtpVerificationRepository otpRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private Msg91Service msg91Service;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private JwtService jwtService;
    private OtpService otpService;
    private UserService userService;

    private final String secretKey = "0123456789012345678901234567890123456789012345678901234567890123";
    private final String phone = "9876543210";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secretKey);
        otpService = new OtpService(otpRepository, jwtService, msg91Service, userRepository, passwordEncoder, jdbcTemplate);
        userService = new UserService(userRepository, jwtService, employeeRepository, passwordEncoder, loginAttemptService, jdbcTemplate);
    }

    private OtpVerification createTestOtp(String plainOtp, OtpPurpose purpose, boolean verified, int attempts, Instant expiresAt) {
        OtpVerification v = new OtpVerification();
        v.setPhoneNumber(phone);
        v.setPurpose(purpose);
        v.setOtp("HASHED_" + plainOtp);
        v.setVerified(verified);
        v.setAttempts(attempts);
        v.setCreatedAt(Instant.now());
        v.setExpiresAt(expiresAt);
        return v;
    }

    // =========================================================================
    // 1. AUTH STATUS CHECKS
    // =========================================================================

    @Test
    @DisplayName("Staff phone numbers are detected and blocked from customer auth status")
    void checkCustomerAuthStatus_StaffAccount_ReturnsStaffAccount() {
        User staff = new User();
        staff.setPhoneNumber(phone);
        staff.setRole(Role.EMPLOYEE);
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(staff));

        CustomerAuthStatusResponse res = otpService.checkCustomerAuthStatus(phone);
        assertEquals("STAFF_ACCOUNT", res.getStatus());
        assertTrue(res.getMessage().contains("Staff accounts must log in using password"));
    }

    @Test
    @DisplayName("Legacy or new customers with customerEnrolled=false return NOT_ENROLLED")
    void checkCustomerAuthStatus_UnenrolledCustomer_ReturnsNotEnrolled() {
        User legacyCustomer = new User();
        legacyCustomer.setPhoneNumber(phone);
        legacyCustomer.setRole(Role.CUSTOMER);
        legacyCustomer.setCustomerEnrolled(false);
        legacyCustomer.setPassword("old_legacy_password");
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(legacyCustomer));

        CustomerAuthStatusResponse res = otpService.checkCustomerAuthStatus(phone);
        assertEquals("NOT_ENROLLED", res.getStatus());
    }

    @Test
    @DisplayName("Fully enrolled customers with customerEnrolled=true return ENROLLED")
    void checkCustomerAuthStatus_EnrolledCustomer_ReturnsEnrolled() {
        User enrolled = new User();
        enrolled.setPhoneNumber(phone);
        enrolled.setRole(Role.CUSTOMER);
        enrolled.setCustomerEnrolled(true);
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(enrolled));

        CustomerAuthStatusResponse res = otpService.checkCustomerAuthStatus(phone);
        assertEquals("ENROLLED", res.getStatus());
    }

    // =========================================================================
    // 2. ENROLLMENT OTP DISPATCH
    // =========================================================================

    @Test
    @DisplayName("Enrolled customer cannot request enrollment OTP (prevents re-enrollment)")
    void sendCustomerEnrollmentOtp_AlreadyEnrolled_Rejected() {
        User enrolled = new User();
        enrolled.setPhoneNumber(phone);
        enrolled.setRole(Role.CUSTOMER);
        enrolled.setCustomerEnrolled(true);
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(enrolled));

        SendOtpRequest req = new SendOtpRequest();
        req.setPhoneNumber(phone);
        OtpResponse res = otpService.sendCustomerEnrollmentOtp(req);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("already enrolled"));
    }

    @Test
    @DisplayName("Staff account cannot request customer enrollment OTP")
    void sendCustomerEnrollmentOtp_StaffAccount_Rejected() {
        User owner = new User();
        owner.setPhoneNumber(phone);
        owner.setRole(Role.OWNER);
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(owner));

        SendOtpRequest req = new SendOtpRequest();
        req.setPhoneNumber(phone);
        OtpResponse res = otpService.sendCustomerEnrollmentOtp(req);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Staff accounts must log in"));
    }

    @Test
    @DisplayName("Unenrolled customer successfully receives enrollment OTP")
    void sendCustomerEnrollmentOtp_UnenrolledCustomer_Success() {
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.countByPhoneNumberAndPurposeAndCreatedAtAfter(eq(phone), eq(OtpPurpose.CUSTOMER_ENROLLMENT), any())).thenReturn(0L);
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("HASHED_OTP");

        SendOtpRequest req = new SendOtpRequest();
        req.setPhoneNumber(phone);
        OtpResponse res = otpService.sendCustomerEnrollmentOtp(req);
        assertTrue(res.isSuccess());
        verify(msg91Service).sendOtp(eq(phone), any());
        verify(otpRepository).save(any(OtpVerification.class));
    }

    // =========================================================================
    // 3. ATOMIC ENROLLMENT COMPLETION & SECURE OTP VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("Brand new customer enrollment: creates User with customerEnrolled=true and issues JWT")
    void completeCustomerEnrollment_NewCustomer_Success() {
        String plainOtp = "123456";
        String password = "SecurePassword123";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_ENROLLMENT, false, 0, Instant.now().plusSeconds(300));

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT)).thenReturn(Optional.of(v));
        when(passwordEncoder.matches(plainOtp, v.getOtp())).thenReturn(true);
        when(passwordEncoder.encode(password)).thenReturn("HASHED_PWD");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        OtpResponse res = otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, plainOtp, password));

        assertTrue(res.isSuccess());
        assertNotNull(res.getToken());
        assertEquals(phone, jwtService.extractPhoneNumber(res.getToken()));
        assertEquals("CUSTOMER", jwtService.extractRole(res.getToken()));

        // OTP is deleted to prevent replay
        verify(otpRepository).delete(v);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(phone, saved.getPhoneNumber());
        assertEquals(Role.CUSTOMER, saved.getRole());
        assertTrue(saved.isCustomerEnrolled());
        assertEquals("HASHED_PWD", saved.getPassword());
    }

    @Test
    @DisplayName("Legacy unenrolled customer enrollment: preserves existing record and marks customerEnrolled=true")
    void completeCustomerEnrollment_LegacyCustomer_PreservesIdentity() {
        String plainOtp = "123456";
        String password = "NewPassword123";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_ENROLLMENT, false, 0, Instant.now().plusSeconds(300));

        User existingLegacyUser = new User();
        existingLegacyUser.setPhoneNumber(phone);
        existingLegacyUser.setRole(Role.CUSTOMER);
        existingLegacyUser.setCustomerEnrolled(false);
        existingLegacyUser.setPassword(null);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(existingLegacyUser));
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT)).thenReturn(Optional.of(v));
        when(passwordEncoder.matches(plainOtp, v.getOtp())).thenReturn(true);
        when(passwordEncoder.encode(password)).thenReturn("NEW_HASHED_PWD");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        OtpResponse res = otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, plainOtp, password));

        assertTrue(res.isSuccess());
        assertNotNull(res.getToken());
        assertTrue(existingLegacyUser.isCustomerEnrolled());
        assertEquals("NEW_HASHED_PWD", existingLegacyUser.getPassword());
        verify(otpRepository).delete(v);
    }

    @Test
    @DisplayName("Invalid OTP increments attempts and rejects enrollment")
    void completeCustomerEnrollment_InvalidOtp_IncrementsAttempts() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_ENROLLMENT, false, 2, Instant.now().plusSeconds(300));

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT)).thenReturn(Optional.of(v));
        when(passwordEncoder.matches("999999", v.getOtp())).thenReturn(false);

        OtpResponse res = otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, "999999", "SecurePassword123"));

        assertFalse(res.isSuccess());
        assertEquals("Invalid OTP", res.getMessage());
        assertEquals(3, v.getAttempts());
        verify(otpRepository).save(v);
        verify(otpRepository, never()).delete(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Expired OTP is rejected and purged from database")
    void completeCustomerEnrollment_ExpiredOtp_RejectedAndDeleted() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_ENROLLMENT, false, 0, Instant.now().minusSeconds(10));

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT)).thenReturn(Optional.of(v));

        OtpResponse res = otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, plainOtp, "SecurePassword123"));

        assertFalse(res.isSuccess());
        assertEquals("OTP expired", res.getMessage());
        verify(otpRepository).delete(v);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Replay attempt with deleted OTP fails with 'OTP not found'")
    void completeCustomerEnrollment_OtpReplay_Rejected() {
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT)).thenReturn(Optional.empty());

        OtpResponse res = otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, "123456", "SecurePassword123"));

        assertFalse(res.isSuccess());
        assertEquals("OTP not found", res.getMessage());
    }

    @Test
    @DisplayName("Attempting to enroll an already enrolled customer is rejected on backend")
    void completeCustomerEnrollment_AlreadyEnrolled_Rejected() {
        User enrolled = new User();
        enrolled.setPhoneNumber(phone);
        enrolled.setRole(Role.CUSTOMER);
        enrolled.setCustomerEnrolled(true);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(enrolled));

        OtpResponse res = otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, "123456", "SecurePassword123"));

        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("already enrolled"));
        verify(otpRepository, never()).delete(any());
    }

    // =========================================================================
    // 4. PASSWORD LOGIN BACKEND ENROLLMENT ENFORCEMENT
    // =========================================================================

    @Test
    @DisplayName("Customer login fails with AccountNotEnrolledException if customerEnrolled=false even if password exists")
    void customerPasswordLogin_UnenrolledCustomer_ThrowsAccountNotEnrolledException() {
        User legacyCustomer = new User();
        legacyCustomer.setPhoneNumber(phone);
        legacyCustomer.setRole(Role.CUSTOMER);
        legacyCustomer.setPassword("hashed_old_password");
        legacyCustomer.setCustomerEnrolled(false);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(legacyCustomer));

        LoginRequest req = new LoginRequest();
        req.setPhoneNumber(phone);
        req.setPassword("any_password");

        assertThrows(AccountNotEnrolledException.class, () ->
                userService.login(req)
        );
    }

    @Test
    @DisplayName("Enrolled customer can log in with password successfully")
    void customerPasswordLogin_EnrolledCustomer_Success() {
        User customer = new User();
        customer.setPhoneNumber(phone);
        customer.setRole(Role.CUSTOMER);
        customer.setPassword("HASHED_PWD");
        customer.setCustomerEnrolled(true);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(customer));
        when(loginAttemptService.isLocked(phone)).thenReturn(false);
        when(passwordEncoder.matches("ValidPassword123", "HASHED_PWD")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setPhoneNumber(phone);
        req.setPassword("ValidPassword123");

        AuthResponse res = userService.login(req);

        assertNotNull(res);
        assertNotNull(res.getToken());
        assertEquals("CUSTOMER", res.getRole());
        assertNull(res.getEmployeeId());
        verify(loginAttemptService).loginSucceeded(phone);
    }

    // =========================================================
    // 5. EMPLOYEE & OWNER LOGIN REGRESSION TEST
    // =========================================================

    @Test
    @DisplayName("Employee login works unchanged regardless of customerEnrolled flag")
    void employeeLogin_Regression_Unchanged() {
        User empUser = new User();
        empUser.setPhoneNumber(phone);
        empUser.setRole(Role.EMPLOYEE);
        empUser.setPassword("EMP_HASHED");
        empUser.setCustomerEnrolled(false); // Should be ignored for employees

        Employee emp = mock(Employee.class);
        when(emp.getId()).thenReturn(42L);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(empUser));
        when(loginAttemptService.isLocked(phone)).thenReturn(false);
        when(passwordEncoder.matches("EmpSecret", "EMP_HASHED")).thenReturn(true);
        when(employeeRepository.findByPhoneNumber(phone)).thenReturn(emp);

        LoginRequest req = new LoginRequest();
        req.setPhoneNumber(phone);
        req.setPassword("EmpSecret");

        AuthResponse res = userService.login(req);

        assertNotNull(res);
        assertEquals("EMPLOYEE", res.getRole());
        assertEquals(42L, res.getEmployeeId());
        assertEquals(phone, jwtService.extractPhoneNumber(res.getToken()));
        assertEquals("EMPLOYEE", jwtService.extractRole(res.getToken()));
    }

    @Test
    @DisplayName("Owner login works unchanged regardless of customerEnrolled flag")
    void ownerLogin_Regression_Unchanged() {
        User ownerUser = new User();
        ownerUser.setPhoneNumber(phone);
        ownerUser.setRole(Role.OWNER);
        ownerUser.setPassword("OWNER_HASHED");

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(ownerUser));
        when(loginAttemptService.isLocked(phone)).thenReturn(false);
        when(passwordEncoder.matches("OwnerSecret", "OWNER_HASHED")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setPhoneNumber(phone);
        req.setPassword("OwnerSecret");

        AuthResponse res = userService.login(req);

        assertNotNull(res);
        assertEquals("OWNER", res.getRole());
        assertNull(res.getEmployeeId());
        assertEquals(phone, jwtService.extractPhoneNumber(res.getToken()));
        assertEquals("OWNER", jwtService.extractRole(res.getToken()));
    }

    // =========================================================================
    // 6. RATE LIMITING, COOLDOWN & ATTACK MITIGATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Rate limiting: Requesting more than 5 enrollment OTPs within an hour is blocked")
    void sendCustomerEnrollmentOtp_HourlyRateLimitExceeded_Blocked() {
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.countByPhoneNumberAndPurposeAndCreatedAtAfter(eq(phone), eq(OtpPurpose.CUSTOMER_ENROLLMENT), any()))
                .thenReturn(5L);

        SendOtpRequest req = new SendOtpRequest();
        req.setPhoneNumber(phone);
        OtpResponse res = otpService.sendCustomerEnrollmentOtp(req);

        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Maximum OTP requests reached"));
        verify(msg91Service, never()).sendOtp(any(), any());
    }

    @Test
    @DisplayName("Cooldown: Requesting enrollment OTP within 60 seconds is rejected with remaining time")
    void sendCustomerEnrollmentOtp_ResendCooldownActive_Rejected() {
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.countByPhoneNumberAndPurposeAndCreatedAtAfter(eq(phone), eq(OtpPurpose.CUSTOMER_ENROLLMENT), any()))
                .thenReturn(1L);

        OtpVerification recentOtp = new OtpVerification();
        recentOtp.setCreatedAt(Instant.now().minusSeconds(20)); // only 20s ago
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT))
                .thenReturn(Optional.of(recentOtp));

        SendOtpRequest req = new SendOtpRequest();
        req.setPhoneNumber(phone);
        OtpResponse res = otpService.sendCustomerEnrollmentOtp(req);

        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Please wait"));
        verify(msg91Service, never()).sendOtp(any(), any());
    }

    @Test
    @DisplayName("Enrollment rejects passwords shorter than 4 characters without checking OTP")
    void completeCustomerEnrollment_ShortPassword_Rejected() {
        EnrollCustomerRequest req = new EnrollCustomerRequest(phone, "123456", "abc");
        OtpResponse res = otpService.completeCustomerEnrollment(req);

        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("at least 4 characters"));
        verify(otpRepository, never()).findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("Enrollment accepts simple numeric password '1234'")
    void completeCustomerEnrollment_Password1234_Accepted() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_ENROLLMENT, false, 0, Instant.now().plusSeconds(300));

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT))
                .thenReturn(Optional.of(v));
        when(passwordEncoder.matches(plainOtp, v.getOtp())).thenReturn(true);
        when(passwordEncoder.encode("1234")).thenReturn("HASHED_1234");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        OtpResponse res = otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, plainOtp, "1234"));

        assertTrue(res.isSuccess());
        assertNotNull(res.getToken());
        verify(otpRepository).delete(v);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("HASHED_1234", captor.getValue().getPassword());
    }

    @Test
    @DisplayName("Enrollment accepts simple lowercase password 'asdd'")
    void completeCustomerEnrollment_PasswordAsdd_Accepted() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_ENROLLMENT, false, 0, Instant.now().plusSeconds(300));

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT))
                .thenReturn(Optional.of(v));
        when(passwordEncoder.matches(plainOtp, v.getOtp())).thenReturn(true);
        when(passwordEncoder.encode("asdd")).thenReturn("HASHED_ASDD");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        OtpResponse res = otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, plainOtp, "asdd"));

        assertTrue(res.isSuccess());
        assertNotNull(res.getToken());
        verify(otpRepository).delete(v);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("HASHED_ASDD", captor.getValue().getPassword());
    }

    @Test
    @DisplayName("Maximum 5 invalid OTP attempts purges OTP and locks out further verification")
    void completeCustomerEnrollment_MaxAttemptsExceeded_PurgedAndLocked() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_ENROLLMENT, false, 5, Instant.now().plusSeconds(300));

        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT))
                .thenReturn(Optional.of(v));

        OtpResponse res = otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, plainOtp, "ValidPassword123"));

        assertFalse(res.isSuccess());
        assertEquals("Maximum attempts exceeded", res.getMessage());
        verify(otpRepository).delete(v);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Advisory transaction lock is executed with parameterized SQL during enrollment completion")
    void completeCustomerEnrollment_AdvisoryLockInvoked() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_ENROLLMENT, false, 0, Instant.now().plusSeconds(300));

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT))
                .thenReturn(Optional.of(v));
        when(passwordEncoder.matches(plainOtp, v.getOtp())).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, plainOtp, "ValidPassword123"));

        verify(jdbcTemplate).query(eq("SELECT pg_advisory_xact_lock(hashtext(?))"), any(RowCallbackHandler.class), eq("customer_enroll_" + phone));
    }

    @Test
    @DisplayName("Null JdbcTemplate during customer enrollment throws IllegalStateException rather than silently skipping lock")
    void completeCustomerEnrollment_NullJdbcTemplate_ThrowsIllegalStateException() {
        OtpService serviceWithoutJdbc = new OtpService(otpRepository, jwtService, msg91Service, userRepository, passwordEncoder);

        assertThrows(IllegalStateException.class, () ->
                serviceWithoutJdbc.completeCustomerEnrollment(new EnrollCustomerRequest(phone, "123456", "1234"))
        );

        verify(otpRepository, never()).findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Null JdbcTemplate during password reset throws IllegalStateException rather than silently skipping lock")
    void resetCustomerPassword_NullJdbcTemplate_ThrowsIllegalStateException() {
        OtpService serviceWithoutJdbc = new OtpService(otpRepository, jwtService, msg91Service, userRepository, passwordEncoder);

        CustomerPasswordResetRequest req = new CustomerPasswordResetRequest(phone, "123456", "1234");
        assertThrows(IllegalStateException.class, () ->
                serviceWithoutJdbc.resetCustomerPassword(req)
        );

        verify(otpRepository, never()).findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lock acquisition failure during customer enrollment throws CannotAcquireLockException and aborts without DB modifications")
    void completeCustomerEnrollment_LockAcquisitionFailure_ThrowsExceptionAndNoSave() {
        String plainOtp = "123456";
        doThrow(new org.springframework.dao.CannotAcquireLockException("Lock timeout"))
                .when(jdbcTemplate).query(eq("SELECT pg_advisory_xact_lock(hashtext(?))"), any(RowCallbackHandler.class), eq("customer_enroll_" + phone));

        assertThrows(org.springframework.dao.CannotAcquireLockException.class, () ->
                otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, plainOtp, "1234"))
        );

        verify(otpRepository, never()).findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(any(), any());
        verify(otpRepository, never()).delete(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Replay attempt with already verified OTP fails with 'OTP already used'")
    void completeCustomerEnrollment_AlreadyVerifiedOtp_Rejected() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_ENROLLMENT, true, 0, Instant.now().plusSeconds(300));

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT))
                .thenReturn(Optional.of(v));

        OtpResponse res = otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, plainOtp, "1234"));

        assertFalse(res.isSuccess());
        assertEquals("OTP already used", res.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Unit test: Service thread serialization rejects second concurrent enrollment (PostgreSQL pg_advisory_xact_lock is verified in CustomerAuthConcurrencyIntegrationTest)")
    void completeCustomerEnrollment_ConcurrentRequests_OnlyOneSucceeds() throws Exception {
        String plainOtp = "123456";
        java.util.concurrent.atomic.AtomicBoolean otpConsumed = new java.util.concurrent.atomic.AtomicBoolean(false);
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_ENROLLMENT, false, 0, Instant.now().plusSeconds(300));
        java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();

        doAnswer(inv -> {
            lock.lock();
            return null;
        }).when(jdbcTemplate).query(eq("SELECT pg_advisory_xact_lock(hashtext(?))"), any(RowCallbackHandler.class), eq("customer_enroll_" + phone));

        when(userRepository.findByPhoneNumber(phone)).thenAnswer(inv -> {
            if (otpConsumed.get()) {
                User enrolled = new User();
                enrolled.setPhoneNumber(phone);
                enrolled.setRole(Role.CUSTOMER);
                enrolled.setCustomerEnrolled(true);
                return Optional.of(enrolled);
            }
            return Optional.empty();
        });

        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_ENROLLMENT))
                .thenAnswer(inv -> otpConsumed.get() ? Optional.empty() : Optional.of(v));

        when(passwordEncoder.matches(plainOtp, v.getOtp())).thenReturn(true);
        when(passwordEncoder.encode("1234")).thenReturn("HASHED_1234");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            try {
                otpConsumed.set(true);
                return inv.getArgument(0);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);

        java.util.concurrent.Future<OtpResponse> future1 = executor.submit(() -> {
            barrier.await();
            try {
                return otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, plainOtp, "1234"));
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });
        java.util.concurrent.Future<OtpResponse> future2 = executor.submit(() -> {
            barrier.await();
            try {
                return otpService.completeCustomerEnrollment(new EnrollCustomerRequest(phone, plainOtp, "1234"));
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });

        OtpResponse res1 = future1.get();
        OtpResponse res2 = future2.get();
        executor.shutdown();

        int successCount = (res1.isSuccess() ? 1 : 0) + (res2.isSuccess() ? 1 : 0);
        assertEquals(1, successCount, "Exactly one concurrent enrollment request must succeed");
    }

    @Test
    @DisplayName("Invalid phone number format is rejected before any DB operations")
    void checkCustomerAuthStatus_InvalidPhoneNumber_Rejected() {
        CustomerAuthStatusResponse res = otpService.checkCustomerAuthStatus("12345");
        assertEquals("INVALID", res.getStatus());
        verify(userRepository, never()).findByPhoneNumber(any());
    }

    // =========================================================================
    // 7. CUSTOMER PASSWORD RESET FLOW TESTS
    // =========================================================================

    @Test
    @DisplayName("Password reset OTP dispatch rejected for unenrolled customer")
    void sendCustomerResetOtp_UnenrolledCustomer_Rejected() {
        User unenrolled = new User();
        unenrolled.setPhoneNumber(phone);
        unenrolled.setRole(Role.CUSTOMER);
        unenrolled.setCustomerEnrolled(false);
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(unenrolled));

        OtpResponse res = otpService.sendCustomerResetOtp(phone);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Enrolled customer account not found"));
        verify(msg91Service, never()).sendOtp(any(), any());
    }

    @Test
    @DisplayName("Password reset OTP dispatch succeeds for enrolled customer")
    void sendCustomerResetOtp_EnrolledCustomer_Success() {
        User enrolled = new User();
        enrolled.setPhoneNumber(phone);
        enrolled.setRole(Role.CUSTOMER);
        enrolled.setCustomerEnrolled(true);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(enrolled));
        when(otpRepository.countByPhoneNumberAndPurposeAndCreatedAtAfter(eq(phone), eq(OtpPurpose.CUSTOMER_PASSWORD_RESET), any()))
                .thenReturn(0L);
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_PASSWORD_RESET))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("HASHED_OTP");

        OtpResponse res = otpService.sendCustomerResetOtp(phone);
        assertTrue(res.isSuccess());
        verify(msg91Service).sendOtp(eq(phone), any());
        verify(otpRepository).save(any(OtpVerification.class));
    }

    @Test
    @DisplayName("Password reset completion succeeds with valid OTP and deletes OTP")
    void resetCustomerPassword_ValidOtp_Success() {
        String plainOtp = "123456";
        String newPassword = "NewStrongPassword123";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_PASSWORD_RESET, false, 0, Instant.now().plusSeconds(300));

        User enrolled = new User();
        enrolled.setPhoneNumber(phone);
        enrolled.setRole(Role.CUSTOMER);
        enrolled.setCustomerEnrolled(true);
        enrolled.setPassword("OLD_HASH");

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(enrolled));
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_PASSWORD_RESET))
                .thenReturn(Optional.of(v));
        when(passwordEncoder.matches(plainOtp, v.getOtp())).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn("NEW_HASH");

        CustomerPasswordResetRequest req = new CustomerPasswordResetRequest(phone, plainOtp, newPassword);
        OtpResponse res = otpService.resetCustomerPassword(req);

        assertTrue(res.isSuccess());
        assertEquals("NEW_HASH", enrolled.getPassword());
        verify(userRepository).save(enrolled);
        verify(otpRepository).delete(v);
        verify(jdbcTemplate).query(eq("SELECT pg_advisory_xact_lock(hashtext(?))"), any(RowCallbackHandler.class), eq("customer_reset_" + phone));
    }

    @Test
    @DisplayName("Lock acquisition failure during password reset throws CannotAcquireLockException and aborts without DB modifications")
    void resetCustomerPassword_LockAcquisitionFailure_ThrowsExceptionAndNoSave() {
        String plainOtp = "123456";
        doThrow(new org.springframework.dao.CannotAcquireLockException("Lock timeout"))
                .when(jdbcTemplate).query(eq("SELECT pg_advisory_xact_lock(hashtext(?))"), any(RowCallbackHandler.class), eq("customer_reset_" + phone));

        CustomerPasswordResetRequest req = new CustomerPasswordResetRequest(phone, plainOtp, "1234");
        assertThrows(org.springframework.dao.CannotAcquireLockException.class, () ->
                otpService.resetCustomerPassword(req)
        );

        verify(otpRepository, never()).findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(any(), any());
        verify(otpRepository, never()).delete(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Replay attempt on password reset with already verified OTP fails with 'OTP already used.'")
    void resetCustomerPassword_AlreadyVerifiedOtp_Rejected() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_PASSWORD_RESET, true, 0, Instant.now().plusSeconds(300));

        User enrolled = new User();
        enrolled.setPhoneNumber(phone);
        enrolled.setRole(Role.CUSTOMER);
        enrolled.setCustomerEnrolled(true);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(enrolled));
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_PASSWORD_RESET))
                .thenReturn(Optional.of(v));

        CustomerPasswordResetRequest req = new CustomerPasswordResetRequest(phone, plainOtp, "1234");
        OtpResponse res = otpService.resetCustomerPassword(req);

        assertFalse(res.isSuccess());
        assertEquals("OTP already used.", res.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Replay attempt on password reset with deleted OTP fails with 'Password reset OTP not found.'")
    void resetCustomerPassword_DeletedOtpReplay_Rejected() {
        User enrolled = new User();
        enrolled.setPhoneNumber(phone);
        enrolled.setRole(Role.CUSTOMER);
        enrolled.setCustomerEnrolled(true);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(enrolled));
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_PASSWORD_RESET))
                .thenReturn(Optional.empty());

        CustomerPasswordResetRequest req = new CustomerPasswordResetRequest(phone, "123456", "1234");
        OtpResponse res = otpService.resetCustomerPassword(req);

        assertFalse(res.isSuccess());
        assertEquals("Password reset OTP not found.", res.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Unit test: Service thread serialization rejects second concurrent password reset (PostgreSQL pg_advisory_xact_lock is verified in CustomerAuthConcurrencyIntegrationTest)")
    void resetCustomerPassword_ConcurrentRequests_OnlyOneSucceeds() throws Exception {
        String plainOtp = "123456";
        java.util.concurrent.atomic.AtomicBoolean otpConsumed = new java.util.concurrent.atomic.AtomicBoolean(false);
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_PASSWORD_RESET, false, 0, Instant.now().plusSeconds(300));
        java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();

        doAnswer(inv -> {
            lock.lock();
            return null;
        }).when(jdbcTemplate).query(eq("SELECT pg_advisory_xact_lock(hashtext(?))"), any(RowCallbackHandler.class), eq("customer_reset_" + phone));

        User enrolled = new User();
        enrolled.setPhoneNumber(phone);
        enrolled.setRole(Role.CUSTOMER);
        enrolled.setCustomerEnrolled(true);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(enrolled));
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_PASSWORD_RESET))
                .thenAnswer(inv -> otpConsumed.get() ? Optional.empty() : Optional.of(v));

        when(passwordEncoder.matches(plainOtp, v.getOtp())).thenReturn(true);
        when(passwordEncoder.encode("1234")).thenReturn("HASHED_1234");
        doAnswer(inv -> {
            try {
                otpConsumed.set(true);
                return null;
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }).when(otpRepository).delete(v);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);

        java.util.concurrent.Future<OtpResponse> future1 = executor.submit(() -> {
            barrier.await();
            try {
                return otpService.resetCustomerPassword(new CustomerPasswordResetRequest(phone, plainOtp, "1234"));
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });
        java.util.concurrent.Future<OtpResponse> future2 = executor.submit(() -> {
            barrier.await();
            try {
                return otpService.resetCustomerPassword(new CustomerPasswordResetRequest(phone, plainOtp, "1234"));
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });

        OtpResponse res1 = future1.get();
        OtpResponse res2 = future2.get();
        executor.shutdown();

        int successCount = (res1.isSuccess() ? 1 : 0) + (res2.isSuccess() ? 1 : 0);
        assertEquals(1, successCount, "Exactly one concurrent password reset request must succeed");
    }

    @Test
    @DisplayName("Password reset completion with invalid OTP increments attempts and retains OTP")
    void resetCustomerPassword_InvalidOtp_IncrementsAttempts() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_PASSWORD_RESET, false, 1, Instant.now().plusSeconds(300));

        User enrolled = new User();
        enrolled.setPhoneNumber(phone);
        enrolled.setRole(Role.CUSTOMER);
        enrolled.setCustomerEnrolled(true);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(enrolled));
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_PASSWORD_RESET))
                .thenReturn(Optional.of(v));
        when(passwordEncoder.matches("999999", v.getOtp())).thenReturn(false);

        CustomerPasswordResetRequest req = new CustomerPasswordResetRequest(phone, "999999", "NewStrongPassword123");
        OtpResponse res = otpService.resetCustomerPassword(req);

        assertFalse(res.isSuccess());
        assertEquals("Invalid OTP.", res.getMessage());
        assertEquals(2, v.getAttempts());
        verify(otpRepository).save(v);
        verify(otpRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Password reset completion with expired OTP is deleted and rejected")
    void resetCustomerPassword_ExpiredOtp_DeletedAndRejected() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_PASSWORD_RESET, false, 0, Instant.now().minusSeconds(10));

        User enrolled = new User();
        enrolled.setPhoneNumber(phone);
        enrolled.setRole(Role.CUSTOMER);
        enrolled.setCustomerEnrolled(true);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(enrolled));
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_PASSWORD_RESET))
                .thenReturn(Optional.of(v));

        CustomerPasswordResetRequest req = new CustomerPasswordResetRequest(phone, plainOtp, "NewStrongPassword123");
        OtpResponse res = otpService.resetCustomerPassword(req);

        assertFalse(res.isSuccess());
        assertEquals("OTP expired. Please request a new OTP.", res.getMessage());
        verify(otpRepository).delete(v);
    }

    @Test
    @DisplayName("Password reset rejects new passwords shorter than 4 characters without checking OTP")
    void resetCustomerPassword_ShortPassword_Rejected() {
        CustomerPasswordResetRequest req = new CustomerPasswordResetRequest(phone, "123456", "12");
        OtpResponse res = otpService.resetCustomerPassword(req);

        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("at least 4 characters"));
        verify(otpRepository, never()).findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("Password reset accepts simple numeric password '1234'")
    void resetCustomerPassword_Password1234_Accepted() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_PASSWORD_RESET, false, 0, Instant.now().plusSeconds(300));

        User enrolled = new User();
        enrolled.setPhoneNumber(phone);
        enrolled.setRole(Role.CUSTOMER);
        enrolled.setCustomerEnrolled(true);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(enrolled));
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_PASSWORD_RESET))
                .thenReturn(Optional.of(v));
        when(passwordEncoder.matches(plainOtp, v.getOtp())).thenReturn(true);
        when(passwordEncoder.encode("1234")).thenReturn("HASHED_1234");

        CustomerPasswordResetRequest req = new CustomerPasswordResetRequest(phone, plainOtp, "1234");
        OtpResponse res = otpService.resetCustomerPassword(req);

        assertTrue(res.isSuccess());
        assertEquals("HASHED_1234", enrolled.getPassword());
        verify(userRepository).save(enrolled);
        verify(otpRepository).delete(v);
    }

    @Test
    @DisplayName("Password reset accepts simple lowercase password 'asdd'")
    void resetCustomerPassword_PasswordAsdd_Accepted() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, OtpPurpose.CUSTOMER_PASSWORD_RESET, false, 0, Instant.now().plusSeconds(300));

        User enrolled = new User();
        enrolled.setPhoneNumber(phone);
        enrolled.setRole(Role.CUSTOMER);
        enrolled.setCustomerEnrolled(true);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(enrolled));
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_PASSWORD_RESET))
                .thenReturn(Optional.of(v));
        when(passwordEncoder.matches(plainOtp, v.getOtp())).thenReturn(true);
        when(passwordEncoder.encode("asdd")).thenReturn("HASHED_ASDD");

        CustomerPasswordResetRequest req = new CustomerPasswordResetRequest(phone, plainOtp, "asdd");
        OtpResponse res = otpService.resetCustomerPassword(req);

        assertTrue(res.isSuccess());
        assertEquals("HASHED_ASDD", enrolled.getPassword());
        verify(userRepository).save(enrolled);
        verify(otpRepository).delete(v);
    }

    @Test
    @DisplayName("Enrolled customer login succeeds with simple numeric password '1234'")
    void customerPasswordLogin_Password1234_Success() {
        User customer = new User();
        customer.setPhoneNumber(phone);
        customer.setRole(Role.CUSTOMER);
        customer.setPassword("HASHED_1234");
        customer.setCustomerEnrolled(true);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(customer));
        when(loginAttemptService.isLocked(phone)).thenReturn(false);
        when(passwordEncoder.matches("1234", "HASHED_1234")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setPhoneNumber(phone);
        req.setPassword("1234");

        AuthResponse res = userService.login(req);

        assertNotNull(res);
        assertNotNull(res.getToken());
        assertEquals("CUSTOMER", res.getRole());
        verify(loginAttemptService).loginSucceeded(phone);
    }

    @Test
    @DisplayName("Enrolled customer login succeeds with simple lowercase password 'asdd'")
    void customerPasswordLogin_PasswordAsdd_Success() {
        User customer = new User();
        customer.setPhoneNumber(phone);
        customer.setRole(Role.CUSTOMER);
        customer.setPassword("HASHED_ASDD");
        customer.setCustomerEnrolled(true);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(customer));
        when(loginAttemptService.isLocked(phone)).thenReturn(false);
        when(passwordEncoder.matches("asdd", "HASHED_ASDD")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setPhoneNumber(phone);
        req.setPassword("asdd");

        AuthResponse res = userService.login(req);

        assertNotNull(res);
        assertNotNull(res.getToken());
        assertEquals("CUSTOMER", res.getRole());
        verify(loginAttemptService).loginSucceeded(phone);
    }
}
