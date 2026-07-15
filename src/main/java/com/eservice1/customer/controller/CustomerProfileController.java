package com.eservice1.customer.controller;

import com.eservice1.customer.entity.CustomerProfile;
import com.eservice1.customer.service.CustomerProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/profile")
public class CustomerProfileController {

    private final CustomerProfileService service;

    public CustomerProfileController(
            CustomerProfileService service) {

        this.service = service;
    }

    @PostMapping
    public CustomerProfile save(
            @RequestBody CustomerProfile profile) {

        return service.save(profile);
    }

    @GetMapping("/{phoneNumber}")
    public CustomerProfile getProfile(
            @PathVariable String phoneNumber) {

        System.out.println("CONTROLLER HIT: " + phoneNumber);

        return service.getByPhone(phoneNumber);
    }
}