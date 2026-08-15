package com.eservice1.customer.controller;

import com.eservice1.customer.entity.CustomerProfile;
import com.eservice1.customer.service.CustomerProfileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.eservice1.submission.service.RequestAccessService;

@RestController
@RequestMapping("/customer/profile")
public class CustomerProfileController {

    private final CustomerProfileService service;
    private final RequestAccessService requestAccessService;

    public CustomerProfileController(
            CustomerProfileService service,
            RequestAccessService requestAccessService) {

        this.service = service;
        this.requestAccessService = requestAccessService;
    }

    @PostMapping
    public CustomerProfile save(
            @RequestBody CustomerProfile profile,
            Authentication authentication) {

        return service.save(profile, authentication.getName());
    }

    @GetMapping("/{phoneNumber}")
    public CustomerProfile getProfile(
            @PathVariable String phoneNumber,
            Authentication authentication) {

        requestAccessService.requireCustomerPhone(phoneNumber, authentication);
        return service.getByPhone(phoneNumber);
    }
}
