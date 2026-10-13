package com.eservice1.customer.controller;

import com.eservice1.customer.dto.CustomerAuthStatusResponse;
import com.eservice1.customer.dto.CustomerPasswordResetRequest;
import com.eservice1.customer.dto.EnrollCustomerRequest;
import com.eservice1.customer.dto.OtpResponse;
import com.eservice1.customer.dto.SendOtpRequest;
import com.eservice1.customer.dto.VerifyOtpRequest;
import com.eservice1.customer.service.OtpService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer")
@CrossOrigin
public class OtpController {

    private final OtpService service;

    public OtpController(OtpService service) {
        this.service = service;
    }

    @PostMapping("/auth/status")
    public CustomerAuthStatusResponse checkAuthStatus(
            @RequestBody SendOtpRequest request) {

        return service.checkCustomerAuthStatus(request.getPhoneNumber());
    }

    @PostMapping("/send-otp")
    public OtpResponse sendOtp(
            @RequestBody SendOtpRequest request) {

        return service.sendOtp(request);
    }

    @PostMapping("/verify-otp")
    public OtpResponse verifyOtp(
            @RequestBody VerifyOtpRequest request) {

        return service.verifyOtp(request);
    }

    @PostMapping("/enroll/send-otp")
    public OtpResponse sendEnrollmentOtp(
            @Valid @RequestBody SendOtpRequest request) {

        return service.sendCustomerEnrollmentOtp(request);
    }

    @PostMapping("/enroll/complete")
    public OtpResponse completeEnrollment(
            @Valid @RequestBody EnrollCustomerRequest request) {

        return service.completeCustomerEnrollment(request);
    }

    @PostMapping("/forgot-password/send-otp")
    public OtpResponse sendCustomerForgotPasswordOtp(
            @Valid @RequestBody SendOtpRequest request) {

        return service.sendCustomerResetOtp(request.getPhoneNumber());
    }

    @PostMapping("/forgot-password/reset")
    public OtpResponse resetCustomerPassword(
            @Valid @RequestBody CustomerPasswordResetRequest request) {

        return service.resetCustomerPassword(request);
    }

}