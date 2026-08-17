package com.eservice1.employee.controller;

import com.eservice1.customer.dto.OtpResponse;
import com.eservice1.customer.dto.SendOtpRequest;
import com.eservice1.customer.dto.VerifyOtpRequest;
import com.eservice1.customer.service.OtpService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/employee/forgot-password")
@CrossOrigin
public class EmployeePasswordResetController {

    private final OtpService otpService;

    public EmployeePasswordResetController(
            OtpService otpService) {

        this.otpService = otpService;
    }

    // =========================================
    // SEND OTP
    // =========================================

    @PostMapping("/send-otp")
    public OtpResponse sendOtp(
            @RequestBody SendOtpRequest request) {

        return otpService.sendEmployeeResetOtp(
                request.getPhoneNumber()
        );
    }

    // =========================================
    // VERIFY OTP
    // =========================================

    @PostMapping("/verify-otp")
    public OtpResponse verifyOtp(
            @RequestBody VerifyOtpRequest request) {

        return otpService.verifyEmployeeResetOtp(
                request.getPhoneNumber(),
                request.getOtp()
        );
    }

    // =========================================
    // RESET PASSWORD
    // =========================================

    @PostMapping("/reset")
    public OtpResponse resetPassword(
            @RequestBody ResetPasswordRequest request) {

        return otpService.resetEmployeePassword(
                request.getPhoneNumber(),
                request.getNewPassword()
        );
    }

    // =========================================
    // RESET PASSWORD REQUEST DTO
    // =========================================

    public static class ResetPasswordRequest {

        private String phoneNumber;

        private String newPassword;

        public ResetPasswordRequest() {
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(
                String phoneNumber) {

            this.phoneNumber = phoneNumber;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(
                String newPassword) {

            this.newPassword = newPassword;
        }
    }
}