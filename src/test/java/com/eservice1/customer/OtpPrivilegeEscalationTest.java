package com.eservice1.customer;

import com.eservice1.common.Role;
import com.eservice1.config.JwtAuthenticationEntryPoint;
import com.eservice1.config.JwtFilter;
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
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OtpPrivilegeEscalationTest {

    @Mock
    private OtpVerificationRepository otpRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private Msg91Service msg91Service;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtAuthenticationEntryPoint authenticationEntryPoint;

    private JwtService jwtService;
    private OtpService otpService;
    private JwtFilter jwtFilter;

    private final String secretKey = "0123456789012345678901234567890123456789012345678901234567890123";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = new JwtService(secretKey);
        otpService = new OtpService(otpRepository, jwtService, msg91Service, userRepository, passwordEncoder);
        jwtFilter = new JwtFilter(jwtService, userRepository, authenticationEntryPoint);
    }

    private OtpVerification createMockOtp(String phone, String otp) {
        OtpVerification verification = new OtpVerification();
        verification.setPhoneNumber(phone);
        verification.setOtp(otp);
        verification.setPurpose(OtpPurpose.CUSTOMER_LOGIN);
        verification.setCreatedAt(Instant.now());
        verification.setExpiresAt(Instant.now().plusSeconds(300));
        verification.setVerified(false);
        verification.setAttempts(0);
        return verification;
    }

    @Test
    void testCustomerOtp_Success_CustomerTokenIssued() {
        String phone = "9876543210";
        String otp = "123456";
        OtpVerification verification = createMockOtp(phone, otp);

        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_LOGIN))
                .thenReturn(Optional.of(verification));
        when(passwordEncoder.matches(eq(otp), any())).thenReturn(true);
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.empty());

        User newUser = new User();
        newUser.setPhoneNumber(phone);
        newUser.setRole(Role.CUSTOMER);
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber(phone);
        request.setOtp(otp);

        OtpResponse response = otpService.verifyOtp(request);

        assertTrue(response.isSuccess());
        assertEquals("OTP Verified", response.getMessage());
        assertNotNull(response.getToken());

        // Verify that the generated token carries role CUSTOMER
        String roleClaim = jwtService.extractRole(response.getToken());
        assertEquals("CUSTOMER", roleClaim);
    }

    @Test
    void testOwnerPhone_CustomerOtp_MustNotBecomeOwner() {
        String phone = "9876543211";
        String otp = "123456";
        OtpVerification verification = createMockOtp(phone, otp);

        User ownerUser = new User();
        ownerUser.setPhoneNumber(phone);
        ownerUser.setRole(Role.OWNER);

        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_LOGIN))
                .thenReturn(Optional.of(verification));
        when(passwordEncoder.matches(eq(otp), any())).thenReturn(true);
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(ownerUser));

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber(phone);
        request.setOtp(otp);

        OtpResponse response = otpService.verifyOtp(request);

        // Must reject staff account
        assertFalse(response.isSuccess());
        assertNull(response.getToken());
        assertTrue(response.getMessage().toLowerCase().contains("staff"));
    }

    @Test
    void testEmployeePhone_CustomerOtp_MustNotBecomeEmployee() {
        String phone = "9876543212";
        String otp = "123456";
        OtpVerification verification = createMockOtp(phone, otp);

        User employeeUser = new User();
        employeeUser.setPhoneNumber(phone);
        employeeUser.setRole(Role.EMPLOYEE);

        when(otpRepository.findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(phone, OtpPurpose.CUSTOMER_LOGIN))
                .thenReturn(Optional.of(verification));
        when(passwordEncoder.matches(eq(otp), any())).thenReturn(true);
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(employeeUser));

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber(phone);
        request.setOtp(otp);

        OtpResponse response = otpService.verifyOtp(request);

        // Must reject staff account
        assertFalse(response.isSuccess());
        assertNull(response.getToken());
        assertTrue(response.getMessage().toLowerCase().contains("staff"));
    }

    @Test
    void testCustomerScopedToken_CannotAssumeOwnerRoleInJwtFilter() throws Exception {
        String phone = "9876543213";
        // Generate a token explicitly scoped as CUSTOMER
        String customerToken = jwtService.generateToken(phone, Role.CUSTOMER);

        // User in DB is actually OWNER
        User ownerUser = new User();
        ownerUser.setPhoneNumber(phone);
        ownerUser.setRole(Role.OWNER);

        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(ownerUser));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/admin/dashboard");
        request.addHeader("Authorization", "Bearer " + customerToken);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtFilter.doFilter(request, response, filterChain);

        // SecurityContext must not contain authentication
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(authenticationEntryPoint, times(1)).commence(any(), any(), any());
    }
}
