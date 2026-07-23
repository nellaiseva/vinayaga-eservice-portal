package com.eservice1.customer.controller;

import com.eservice1.customer.dto.OtpResponse;
import com.eservice1.customer.dto.SendOtpRequest;
import com.eservice1.customer.dto.VerifyOtpRequest;
import com.eservice1.customer.service.OtpService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer")
@CrossOrigin
public class OtpController {

    private final OtpService service;

    public OtpController(OtpService service) {
        this.service = service;
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

}