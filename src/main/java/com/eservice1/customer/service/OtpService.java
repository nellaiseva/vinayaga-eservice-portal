package com.eservice1.customer.service;

import com.eservice1.common.Role;
import com.eservice1.config.JwtService;
import com.eservice1.customer.dto.OtpResponse;
import com.eservice1.customer.dto.SendOtpRequest;
import com.eservice1.customer.dto.VerifyOtpRequest;
import com.eservice1.customer.entity.OtpPurpose;
import com.eservice1.customer.entity.OtpVerification;
import com.eservice1.customer.repository.OtpVerificationRepository;
import com.eservice1.user.entity.User;
import com.eservice1.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public OtpService(
            OtpVerificationRepository repository,
            JwtService jwtService,
            Msg91Service msg91Service,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.repository = repository;
        this.jwtService = jwtService;
        this.msg91Service = msg91Service;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
}