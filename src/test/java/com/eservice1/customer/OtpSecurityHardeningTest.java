package com.eservice1.customer;

import com.eservice1.common.Role;
import com.eservice1.config.JwtService;
import com.eservice1.customer.dto.OtpResponse;
import com.eservice1.customer.dto.SendOtpRequest;
import com.eservice1.customer.dto.VerifyOtpRequest;
import com.eservice1.customer.entity.OtpPurpose;
import com.eservice1.customer.entity.OtpVerification;
import com.eservice1.customer.repository.OtpVerificationRepository;
import com.eservice1.customer.service.Msg91Service;
import com.eservice1.customer.service.OtpService;
import com.eservice1.user.entity.User;
import com.eservice1.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OtpSecurityHardeningTest {

    @Mock
    private OtpVerificationRepository otpRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private Msg91Service msg91Service;
    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtService jwtService;
    private OtpService otpService;

    private final String secretKey = "0123456789012345678901234567890123456789012345678901234567890123";
    private final String phone = "9876543210";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secretKey);
        otpService = new OtpService(otpRepository, jwtService, msg91Service, userRepository, passwordEncoder);
    }

    private OtpVerification createTestOtp(String plainOtp, boolean verified, int attempts, Instant expiresAt, Instant createdAt) {
        OtpVerification v = new OtpVerification();
        v.setPhoneNumber(phone);
        v.setPurpose(OtpPurpose.CUSTOMER_LOGIN);
        v.setOtp("HASHED_" + plainOtp);
        v.setVerified(verified);
        v.setAttempts(attempts);
        v.setExpiresAt(expiresAt);
        v.setCreatedAt(createdAt);
        return v;
    }

    @Test
    void testCorrectOtp_Success_HashedVerification() {
        String plainOtp = "123456";
        OtpVerification v = createTestOtp(plainOtp, false, 0, Instant.now().plusSeconds(300), Instant.now());

        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_LOGIN))
                .thenReturn(Optional.of(v));
        when(passwordEncoder.matches(eq(plainOtp), eq("HASHED_" + plainOtp))).thenReturn(true);
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());

        User newUser = new User();
        newUser.setPhoneNumber(phone);
        newUser.setRole(Role.CUSTOMER);
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber(phone);
        request.setOtp(plainOtp);

        OtpResponse response = otpService.verifyOtp(request);

        assertTrue(response.isSuccess());
        assertEquals("OTP Verified", response.getMessage());
        assertNotNull(response.getToken());
        verify(otpRepository).delete(v);
    }

    @Test
    void testIncorrectOtp_FailsAndIncrementsAttempts() {
        String enteredOtp = "999999";
        OtpVerification v = createTestOtp("123456", false, 0, Instant.now().plusSeconds(300), Instant.now());

        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_LOGIN))
                .thenReturn(Optional.of(v));
        when(passwordEncoder.matches(eq(enteredOtp), eq("HASHED_123456"))).thenReturn(false);

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber(phone);
        request.setOtp(enteredOtp);

        OtpResponse response = otpService.verifyOtp(request);

        assertFalse(response.isSuccess());
        assertEquals("Invalid OTP", response.getMessage());
        assertEquals(1, v.getAttempts());
        verify(otpRepository).save(v);
    }

    @Test
    void testExpiredOtp_FailsAndDeletesRecord() {
        String plainOtp = "123456";
        // Expired 10 seconds ago
        OtpVerification v = createTestOtp(plainOtp, false, 0, Instant.now().minusSeconds(10), Instant.now().minusSeconds(310));

        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_LOGIN))
                .thenReturn(Optional.of(v));

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber(phone);
        request.setOtp(plainOtp);

        OtpResponse response = otpService.verifyOtp(request);

        assertFalse(response.isSuccess());
        assertEquals("OTP expired", response.getMessage());
        verify(otpRepository).delete(v);
    }

    @Test
    void testReusedOtp_Fails() {
        String plainOtp = "123456";
        // Already verified
        OtpVerification v = createTestOtp(plainOtp, true, 0, Instant.now().plusSeconds(300), Instant.now());

        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_LOGIN))
                .thenReturn(Optional.of(v));

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber(phone);
        request.setOtp(plainOtp);

        OtpResponse response = otpService.verifyOtp(request);

        assertFalse(response.isSuccess());
        assertEquals("OTP already used", response.getMessage());
    }

    @Test
    void testExcessiveAttempts_LockedOutAndDeleted() {
        String plainOtp = "123456";
        // Already reached 5 attempts
        OtpVerification v = createTestOtp(plainOtp, false, 5, Instant.now().plusSeconds(300), Instant.now());

        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_LOGIN))
                .thenReturn(Optional.of(v));

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber(phone);
        request.setOtp(plainOtp);

        OtpResponse response = otpService.verifyOtp(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("Maximum attempts exceeded"));
        verify(otpRepository).delete(v);
    }

    @Test
    void testResendAbuse_CooldownActive_Rejected() {
        // Created 20 seconds ago (cooldown is 60s)
        OtpVerification v = createTestOtp("123456", false, 0, Instant.now().plusSeconds(280), Instant.now().minusSeconds(20));

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.countByPhoneNumberAndPurposeAndCreatedAtAfter(eq(phone), eq(OtpPurpose.CUSTOMER_LOGIN), any()))
                .thenReturn(1L);
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_LOGIN))
                .thenReturn(Optional.of(v));

        SendOtpRequest request = new SendOtpRequest();
        request.setPhoneNumber(phone);

        OtpResponse response = otpService.sendOtp(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("Please wait"));
        verify(msg91Service, never()).sendOtp(any(), any());
    }

    @Test
    void testResendAbuse_HourlyLimitExceeded_Rejected() {
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        // 5 OTPs already requested in the past hour
        when(otpRepository.countByPhoneNumberAndPurposeAndCreatedAtAfter(eq(phone), eq(OtpPurpose.CUSTOMER_LOGIN), any()))
                .thenReturn(5L);

        SendOtpRequest request = new SendOtpRequest();
        request.setPhoneNumber(phone);

        OtpResponse response = otpService.sendOtp(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("Maximum OTP requests reached"));
        verify(msg91Service, never()).sendOtp(any(), any());
    }

    @Test
    void testSendOtp_UsesHashedStorage() {
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());
        when(otpRepository.countByPhoneNumberAndPurposeAndCreatedAtAfter(eq(phone), eq(OtpPurpose.CUSTOMER_LOGIN), any()))
                .thenReturn(0L);
        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_LOGIN))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$encodedOtpHashSample");

        SendOtpRequest request = new SendOtpRequest();
        request.setPhoneNumber(phone);

        OtpResponse response = otpService.sendOtp(request);

        assertTrue(response.isSuccess());

        ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
        verify(otpRepository).save(captor.capture());

        OtpVerification savedVerification = captor.getValue();
        // Verify saved OTP is hashed, NOT plaintext
        assertEquals("$2a$10$encodedOtpHashSample", savedVerification.getOtp());
        verify(msg91Service).sendOtp(eq(phone), anyString());
    }
}
