package com.eservice1.customer.service;

import com.eservice1.common.Role;
import com.eservice1.config.JwtService;
import com.eservice1.customer.dto.CustomerAuthStatusResponse;
import com.eservice1.customer.dto.CustomerPasswordResetRequest;
import com.eservice1.customer.dto.EnrollCustomerRequest;
import com.eservice1.customer.dto.OtpResponse;
import com.eservice1.customer.dto.SendOtpRequest;
import com.eservice1.customer.dto.VerifyOtpRequest;
import com.eservice1.customer.entity.OtpPurpose;
import com.eservice1.customer.entity.OtpVerification;
import com.eservice1.customer.repository.OtpVerificationRepository;
import com.eservice1.user.entity.User;
import com.eservice1.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class OtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OtpVerificationRepository repository;
    private final JwtService jwtService;
    private final Msg91Service msg91Service;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public OtpService(
            OtpVerificationRepository repository,
            JwtService jwtService,
            Msg91Service msg91Service,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this(repository, jwtService, msg91Service, userRepository, passwordEncoder, null);
    }

    @Autowired
    public OtpService(
            OtpVerificationRepository repository,
            JwtService jwtService,
            Msg91Service msg91Service,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate) {

        this.repository = repository;
        this.jwtService = jwtService;
        this.msg91Service = msg91Service;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    // =========================================================
    // CUSTOMER LOGIN OTP
    // =========================================================

    public OtpResponse sendOtp(
            SendOtpRequest request) {

        String phoneNumber =
                request.getPhoneNumber();

        // Prevent staff accounts from receiving/using customer OTPs
        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);
        if (existingUser.isPresent()) {
            Role role = existingUser.get().getRole();
            if (role == Role.OWNER || role == Role.EMPLOYEE) {
                return new OtpResponse(
                        false,
                        "Staff accounts must log in using password at staff login.",
                        null
                );
            }
        }

        OtpPurpose purpose =
                OtpPurpose.CUSTOMER_LOGIN;

        Instant now =
                Instant.now();

        // Maximum 5 OTP requests per hour
        long otpCount =
                repository
                        .countByPhoneNumberAndPurposeAndCreatedAtAfter(
                                phoneNumber,
                                purpose,
                                now.minusSeconds(3600)
                        );

        if (otpCount >= 5) {

            return new OtpResponse(
                    false,
                    "Maximum OTP requests reached. Please try again after one hour.",
                    null
            );
        }

        Optional<OtpVerification> existing =
                repository
                        .findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(
                                phoneNumber,
                                purpose
                        );

        if (existing.isPresent()) {

            OtpVerification oldOtp =
                    existing.get();

            Instant resendAvailableAt =
                    oldOtp.getCreatedAt()
                            .plusSeconds(60);

            if (now.isBefore(resendAvailableAt)) {

                long seconds =
                        Duration.between(
                                now,
                                resendAvailableAt
                        ).getSeconds();

                return new OtpResponse(
                        false,
                        "Please wait " + seconds
                                + " seconds before requesting another OTP.",
                        null
                );
            }

            repository.delete(oldOtp);
        }

        // Generate 6-digit cryptographically secure OTP
        String otp =
                String.valueOf(
                        100000 + SECURE_RANDOM.nextInt(900000)
                );

        Instant createdAt =
                Instant.now();

        OtpVerification verification =
                new OtpVerification();

        verification.setPhoneNumber(phoneNumber);

        verification.setPurpose(
                OtpPurpose.CUSTOMER_LOGIN
        );

        // Store hashed OTP in database
        verification.setOtp(passwordEncoder.encode(otp));

        verification.setCreatedAt(
                createdAt
        );

        verification.setExpiresAt(
                createdAt.plusSeconds(300)
        );

        verification.setVerified(false);

        verification.setAttempts(0);

        repository.save(verification);

        // Send OTP through MSG91
        msg91Service.sendOtp(
                phoneNumber,
                otp
        );

        return new OtpResponse(
                true,
                "OTP sent successfully",
                null
        );
    }

    // =========================================================
    // CUSTOMER LOGIN OTP VERIFICATION
    // =========================================================

    public OtpResponse verifyOtp(
            VerifyOtpRequest request) {

        String phoneNumber =
                request.getPhoneNumber();

        Optional<OtpVerification> optional =
                repository
                        .findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(
                                phoneNumber,
                                OtpPurpose.CUSTOMER_LOGIN
                        );

        if (optional.isEmpty()) {

            return new OtpResponse(
                    false,
                    "OTP not found",
                    null
            );
        }

        OtpVerification verification =
                optional.get();

        if (verification.isVerified()) {

            return new OtpResponse(
                    false,
                    "OTP already used",
                    null
            );
        }

        Instant now =
                Instant.now();

        // Check expiry
        if (now.isAfter(
                verification.getExpiresAt())) {

            repository.delete(verification);

            return new OtpResponse(
                    false,
                    "OTP expired",
                    null
            );
        }

        // Maximum verification attempts
        if (verification.getAttempts() >= 5) {

            repository.delete(verification);

            return new OtpResponse(
                    false,
                    "Maximum attempts exceeded",
                    null
            );
        }

        // Check OTP using BCrypt matching
        if (!passwordEncoder.matches(request.getOtp(), verification.getOtp())) {

            verification.setAttempts(
                    verification.getAttempts() + 1
            );

            repository.save(
                    verification
            );

            return new OtpResponse(
                    false,
                    "Invalid OTP",
                    null
            );
        }

        // OTP is valid
        repository.delete(
                verification
        );

        // Verify user role if already exists - staff accounts MUST NOT authenticate via customer OTP
        Optional<User> existingUser =
                userRepository.findByPhoneNumber(phoneNumber);

        if (existingUser.isPresent()) {
            Role role = existingUser.get().getRole();
            if (role == Role.OWNER || role == Role.EMPLOYEE) {
                return new OtpResponse(
                        false,
                        "Staff accounts must log in using password at staff login.",
                        null
                );
            }
        }

        // Find existing user or create customer
        User user =
                existingUser
                        .orElseGet(() -> {

                            User newUser =
                                    new User();

                            newUser.setPhoneNumber(
                                    phoneNumber
                            );

                            newUser.setRole(
                                    Role.CUSTOMER
                            );

                            return userRepository.save(
                                    newUser
                            );
                        });

        String token =
                jwtService.generateToken(
                        user.getPhoneNumber(),
                        Role.CUSTOMER
                );

        return new OtpResponse(
                true,
                "OTP Verified",
                token
        );
    }

    // =========================================================
    // EMPLOYEE FORGOT PASSWORD - SEND OTP
    // =========================================================

    public OtpResponse sendEmployeeResetOtp(
            String phoneNumber) {

        User user =
                userRepository
                        .findByPhoneNumber(
                                phoneNumber
                        )
                        .orElse(null);

        // Only existing employees can reset password
        if (user == null ||
                user.getRole() != Role.EMPLOYEE) {

            return new OtpResponse(
                    false,
                    "Employee account not found.",
                    null
            );
        }

        OtpPurpose purpose =
                OtpPurpose.EMPLOYEE_PASSWORD_RESET;

        Instant now =
                Instant.now();

        // Maximum 5 OTP requests per hour
        long otpCount =
                repository
                        .countByPhoneNumberAndPurposeAndCreatedAtAfter(
                                phoneNumber,
                                purpose,
                                now.minusSeconds(3600)
                        );

        if (otpCount >= 5) {

            return new OtpResponse(
                    false,
                    "Maximum OTP requests reached. Please try again after one hour.",
                    null
            );
        }

        Optional<OtpVerification> existing =
                repository
                        .findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(
                                phoneNumber,
                                purpose
                        );

        if (existing.isPresent()) {

            OtpVerification oldOtp =
                    existing.get();

            Instant resendAvailableAt =
                    oldOtp.getCreatedAt()
                            .plusSeconds(60);

            if (now.isBefore(resendAvailableAt)) {

                long seconds =
                        Duration.between(
                                now,
                                resendAvailableAt
                        ).getSeconds();

                return new OtpResponse(
                        false,
                        "Please wait " + seconds +
                                " seconds before requesting another OTP.",
                        null
                );
            }

            repository.delete(
                    oldOtp
            );
        }

        // Generate 6-digit cryptographically secure OTP
        String otp =
                String.valueOf(
                        100000 + SECURE_RANDOM.nextInt(900000)
                );

        Instant createdAt =
                Instant.now();

        OtpVerification verification =
                new OtpVerification();

        verification.setPhoneNumber(
                phoneNumber
        );

        verification.setPurpose(
                OtpPurpose.EMPLOYEE_PASSWORD_RESET
        );

        // Store hashed OTP
        verification.setOtp(
                passwordEncoder.encode(otp)
        );

        verification.setCreatedAt(
                createdAt
        );

        verification.setExpiresAt(
                createdAt.plusSeconds(300)
        );

        verification.setVerified(false);

        verification.setAttempts(0);

        repository.save(
                verification
        );

        // Send OTP through MSG91
        msg91Service.sendOtp(
                phoneNumber,
                otp
        );

        return new OtpResponse(
                true,
                "OTP sent successfully",
                null
        );
    }

    // =========================================================
    // EMPLOYEE FORGOT PASSWORD - VERIFY OTP
    // =========================================================

    public OtpResponse verifyEmployeeResetOtp(
            String phoneNumber,
            String otp) {

        Optional<OtpVerification> optional =
                repository
                        .findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(
                                phoneNumber,
                                OtpPurpose.EMPLOYEE_PASSWORD_RESET
                        );

        if (optional.isEmpty()) {

            return new OtpResponse(
                    false,
                    "OTP not found",
                    null
            );
        }

        OtpVerification verification =
                optional.get();

        if (verification.isVerified()) {

            return new OtpResponse(
                    false,
                    "OTP already verified",
                    null
            );
        }

        Instant now =
                Instant.now();

        // Check expiry
        if (now.isAfter(
                verification.getExpiresAt())) {

            repository.delete(verification);

            return new OtpResponse(
                    false,
                    "OTP expired",
                    null
            );
        }

        // Maximum verification attempts
        if (verification.getAttempts() >= 5) {

            repository.delete(verification);

            return new OtpResponse(
                    false,
                    "Maximum attempts exceeded",
                    null
            );
        }

        // Check OTP using BCrypt matching
        if (!passwordEncoder.matches(otp, verification.getOtp())) {

            verification.setAttempts(
                    verification.getAttempts() + 1
            );

            repository.save(
                    verification
            );

            return new OtpResponse(
                    false,
                    "Invalid OTP",
                    null
            );
        }

        // OTP is valid
        verification.setVerified(
                true
        );

        repository.save(
                verification
        );

        return new OtpResponse(
                true,
                "OTP verified successfully",
                null
        );
    }

    // =========================================================
    // EMPLOYEE FORGOT PASSWORD - RESET PASSWORD
    // =========================================================

    public OtpResponse resetEmployeePassword(
            String phoneNumber,
            String newPassword) {

        User user =
                userRepository
                        .findByPhoneNumber(
                                phoneNumber
                        )
                        .orElse(null);

        if (user == null ||
                user.getRole() != Role.EMPLOYEE) {

            return new OtpResponse(
                    false,
                    "Employee account not found.",
                    null
            );
        }

        Optional<OtpVerification> optional =
                repository
                        .findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(
                                phoneNumber,
                                OtpPurpose.EMPLOYEE_PASSWORD_RESET
                        );

        if (optional.isEmpty()) {

            return new OtpResponse(
                    false,
                    "Password reset OTP not found.",
                    null
            );
        }

        OtpVerification verification =
                optional.get();

        // OTP must be verified first
        if (!verification.isVerified()) {

            return new OtpResponse(
                    false,
                    "Please verify OTP first.",
                    null
            );
        }

        // Check OTP expiry
        if (Instant.now()
                .isAfter(
                        verification.getExpiresAt()
                )) {

            repository.delete(
                    verification
            );

            return new OtpResponse(
                    false,
                    "OTP expired. Please request a new OTP.",
                    null
            );
        }

        // Update password
        user.setPassword(
                passwordEncoder.encode(
                        newPassword
                )
        );

        userRepository.save(
                user
        );

        // OTP can no longer be reused
        repository.delete(
                verification
        );

        return new OtpResponse(
                true,
                "Password reset successfully.",
                null
        );
    }

    // =========================================================
    // CUSTOMER AUTH STATUS CHECK
    // =========================================================

    public CustomerAuthStatusResponse checkCustomerAuthStatus(String phoneNumber) {
        if (phoneNumber == null || !phoneNumber.matches("^[6-9]\\d{9}$")) {
            return new CustomerAuthStatusResponse(
                    "INVALID",
                    "Please enter a valid 10-digit mobile number."
            );
        }

        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (user.getRole() == Role.OWNER || user.getRole() == Role.EMPLOYEE) {
                return new CustomerAuthStatusResponse(
                        "STAFF_ACCOUNT",
                        "Staff accounts must log in using password at staff login."
                );
            }

            if (user.getRole() == Role.CUSTOMER && user.isCustomerEnrolled()) {
                return new CustomerAuthStatusResponse(
                        "ENROLLED",
                        "Customer is enrolled."
                );
            }
        }

        return new CustomerAuthStatusResponse(
                "NOT_ENROLLED",
                "Customer requires first-time enrollment via OTP."
        );
    }

    // =========================================================
    // CUSTOMER FIRST-TIME ENROLLMENT - SEND OTP
    // =========================================================

    public OtpResponse sendCustomerEnrollmentOtp(SendOtpRequest request) {
        String phoneNumber = request.getPhoneNumber();
        if (phoneNumber == null || !phoneNumber.matches("^[6-9]\\d{9}$")) {
            return new OtpResponse(
                    false,
                    "Please enter a valid 10-digit mobile number.",
                    null
            );
        }

        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);
        if (existingUser.isPresent()) {
            Role role = existingUser.get().getRole();
            if (role == Role.OWNER || role == Role.EMPLOYEE) {
                return new OtpResponse(
                        false,
                        "Staff accounts must log in using password at staff login.",
                        null
                );
            }

            if (role == Role.CUSTOMER && existingUser.get().isCustomerEnrolled()) {
                return new OtpResponse(
                        false,
                        "Account is already enrolled. Please log in using your password.",
                        null
                );
            }
        }

        OtpPurpose purpose = OtpPurpose.CUSTOMER_ENROLLMENT;
        Instant now = Instant.now();

        // Rate limiting: Maximum 5 OTP requests per hour
        long otpCount = repository.countByPhoneNumberAndPurposeAndCreatedAtAfter(
                phoneNumber,
                purpose,
                now.minusSeconds(3600)
        );

        if (otpCount >= 5) {
            return new OtpResponse(
                    false,
                    "Maximum OTP requests reached. Please try again after one hour.",
                    null
            );
        }

        // Resend cooldown: 60 seconds
        Optional<OtpVerification> existing = repository
                .findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phoneNumber, purpose);

        if (existing.isPresent()) {
            OtpVerification oldOtp = existing.get();
            Instant resendAvailableAt = oldOtp.getCreatedAt().plusSeconds(60);

            if (now.isBefore(resendAvailableAt)) {
                long seconds = Duration.between(now, resendAvailableAt).getSeconds();
                return new OtpResponse(
                        false,
                        "Please wait " + seconds + " seconds before requesting another OTP.",
                        null
                );
            }

            repository.delete(oldOtp);
        }

        // Generate 6-digit cryptographically secure OTP
        String otp = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
        Instant createdAt = Instant.now();

        OtpVerification verification = new OtpVerification();
        verification.setPhoneNumber(phoneNumber);
        verification.setPurpose(purpose);
        verification.setOtp(passwordEncoder.encode(otp));
        verification.setCreatedAt(createdAt);
        verification.setExpiresAt(createdAt.plusSeconds(300));
        verification.setVerified(false);
        verification.setAttempts(0);

        repository.save(verification);

        // Send OTP through MSG91
        msg91Service.sendOtp(phoneNumber, otp);

        return new OtpResponse(
                true,
                "Enrollment OTP sent successfully",
                null
        );
    }

    private void acquireAdvisoryLock(String lockKey) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate is required for transactional advisory locking but is null.");
        }
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> {}, lockKey);
    }

    // =========================================================
    // CUSTOMER FIRST-TIME ENROLLMENT - COMPLETE (ATOMIC)
    // =========================================================

    @Transactional
    public OtpResponse completeCustomerEnrollment(EnrollCustomerRequest request) {
        String phoneNumber = request.getPhoneNumber();
        String plainOtp = request.getOtp();
        String password = request.getPassword();

        if (phoneNumber == null || !phoneNumber.matches("^[6-9]\\d{9}$")) {
            return new OtpResponse(false, "Please enter a valid 10-digit mobile number.", null);
        }

        if (password == null || password.length() < 4) {
            return new OtpResponse(false, "Password must be at least 4 characters.", null);
        }

        // Concurrency control: serialize concurrent enrollment attempts for the same phone number
        acquireAdvisoryLock("customer_enroll_" + phoneNumber);

        // Prevent staff from enrolling as customers
        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);
        if (existingUser.isPresent()) {
            Role role = existingUser.get().getRole();
            if (role == Role.OWNER || role == Role.EMPLOYEE) {
                return new OtpResponse(
                        false,
                        "Staff accounts must log in using password at staff login.",
                        null
                );
            }

            // Prevent overwriting password if already enrolled
            if (role == Role.CUSTOMER && existingUser.get().isCustomerEnrolled()) {
                return new OtpResponse(
                        false,
                        "Account is already enrolled. Please log in using your password.",
                        null
                );
            }
        }

        // Lookup enrollment OTP
        Optional<OtpVerification> optional = repository
                .findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(
                        phoneNumber,
                        OtpPurpose.CUSTOMER_ENROLLMENT
                );

        if (optional.isEmpty()) {
            return new OtpResponse(false, "OTP not found", null);
        }

        OtpVerification verification = optional.get();

        if (verification.isVerified()) {
            return new OtpResponse(false, "OTP already used", null);
        }

        Instant now = Instant.now();

        // Expiry check
        if (now.isAfter(verification.getExpiresAt())) {
            repository.delete(verification);
            return new OtpResponse(false, "OTP expired", null);
        }

        // Attempt limit check
        if (verification.getAttempts() >= 5) {
            repository.delete(verification);
            return new OtpResponse(false, "Maximum attempts exceeded", null);
        }

        // Verify OTP hash
        if (!passwordEncoder.matches(plainOtp, verification.getOtp())) {
            verification.setAttempts(verification.getAttempts() + 1);
            repository.save(verification);
            return new OtpResponse(false, "Invalid OTP", null);
        }

        // OTP verified - delete immediately to prevent replay
        repository.delete(verification);

        // Create or update Customer User
        User user = existingUser.orElseGet(() -> {
            User newUser = new User();
            newUser.setPhoneNumber(phoneNumber);
            newUser.setRole(Role.CUSTOMER);
            return newUser;
        });

        user.setPassword(passwordEncoder.encode(password));
        user.setCustomerEnrolled(true);
        userRepository.save(user);

        // Issue JWT token upon successful enrollment
        String token = jwtService.generateToken(user.getPhoneNumber(), Role.CUSTOMER);

        return new OtpResponse(
                true,
                "Enrollment completed successfully.",
                token
        );
    }

    // =========================================================
    // CUSTOMER FORGOT PASSWORD - SEND OTP
    // =========================================================

    public OtpResponse sendCustomerResetOtp(String phoneNumber) {
        if (phoneNumber == null || !phoneNumber.matches("^[6-9]\\d{9}$")) {
            return new OtpResponse(false, "Please enter a valid 10-digit mobile number.", null);
        }

        User user = userRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (user == null || user.getRole() != Role.CUSTOMER || !user.isCustomerEnrolled()) {
            return new OtpResponse(
                    false,
                    "Enrolled customer account not found for this mobile number.",
                    null
            );
        }

        OtpPurpose purpose = OtpPurpose.CUSTOMER_PASSWORD_RESET;
        Instant now = Instant.now();

        long otpCount = repository.countByPhoneNumberAndPurposeAndCreatedAtAfter(
                phoneNumber,
                purpose,
                now.minusSeconds(3600)
        );

        if (otpCount >= 5) {
            return new OtpResponse(
                    false,
                    "Maximum OTP requests reached. Please try again after one hour.",
                    null
            );
        }

        Optional<OtpVerification> existing = repository
                .findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phoneNumber, purpose);

        if (existing.isPresent()) {
            OtpVerification oldOtp = existing.get();
            Instant resendAvailableAt = oldOtp.getCreatedAt().plusSeconds(60);

            if (now.isBefore(resendAvailableAt)) {
                long seconds = Duration.between(now, resendAvailableAt).getSeconds();
                return new OtpResponse(
                        false,
                        "Please wait " + seconds + " seconds before requesting another OTP.",
                        null
                );
            }

            repository.delete(oldOtp);
        }

        String otp = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
        Instant createdAt = Instant.now();

        OtpVerification verification = new OtpVerification();
        verification.setPhoneNumber(phoneNumber);
        verification.setPurpose(purpose);
        verification.setOtp(passwordEncoder.encode(otp));
        verification.setCreatedAt(createdAt);
        verification.setExpiresAt(createdAt.plusSeconds(300));
        verification.setVerified(false);
        verification.setAttempts(0);

        repository.save(verification);
        msg91Service.sendOtp(phoneNumber, otp);

        return new OtpResponse(true, "Password reset OTP sent successfully", null);
    }

    // =========================================================
    // CUSTOMER FORGOT PASSWORD - RESET PASSWORD
    // =========================================================

    @Transactional
    public OtpResponse resetCustomerPassword(CustomerPasswordResetRequest request) {
        String phoneNumber = request.getPhoneNumber();
        String plainOtp = request.getOtp();
        String newPassword = request.getNewPassword();

        if (phoneNumber == null || !phoneNumber.matches("^[6-9]\\d{9}$")) {
            return new OtpResponse(false, "Please enter a valid 10-digit mobile number.", null);
        }

        if (newPassword == null || newPassword.length() < 4) {
            return new OtpResponse(false, "New password must be at least 4 characters.", null);
        }

        // Concurrency control: serialize concurrent password reset attempts for the same phone number
        acquireAdvisoryLock("customer_reset_" + phoneNumber);

        User user = userRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (user == null || user.getRole() != Role.CUSTOMER || !user.isCustomerEnrolled()) {
            return new OtpResponse(
                    false,
                    "Enrolled customer account not found.",
                    null
            );
        }

        Optional<OtpVerification> optional = repository
                .findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(
                        phoneNumber,
                        OtpPurpose.CUSTOMER_PASSWORD_RESET
                );

        if (optional.isEmpty()) {
            return new OtpResponse(false, "Password reset OTP not found.", null);
        }

        OtpVerification verification = optional.get();

        if (verification.isVerified()) {
            return new OtpResponse(false, "OTP already used.", null);
        }

        Instant now = Instant.now();
        if (now.isAfter(verification.getExpiresAt())) {
            repository.delete(verification);
            return new OtpResponse(false, "OTP expired. Please request a new OTP.", null);
        }

        if (verification.getAttempts() >= 5) {
            repository.delete(verification);
            return new OtpResponse(false, "Maximum attempts exceeded.", null);
        }

        if (!passwordEncoder.matches(plainOtp, verification.getOtp())) {
            verification.setAttempts(verification.getAttempts() + 1);
            repository.save(verification);
            return new OtpResponse(false, "Invalid OTP.", null);
        }

        // Update password and delete OTP
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        repository.delete(verification);

        return new OtpResponse(true, "Password reset successfully. Please login.", null);
    }
}