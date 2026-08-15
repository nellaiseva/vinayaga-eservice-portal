package com.eservice1.customer.controller;

import com.eservice1.customer.dto.CustomerFormResponseDTO;
import com.eservice1.customer.entity.CustomerFormResponse;
import com.eservice1.customer.service.CustomerFormResponseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import org.springframework.security.core.Authentication;
import com.eservice1.submission.service.RequestAccessService;


@RestController
@RequestMapping("/customer-form-responses")
public class CustomerFormResponseController {

    private final CustomerFormResponseService service;
    private final RequestAccessService requestAccessService;

    public CustomerFormResponseController(
            CustomerFormResponseService service,
            RequestAccessService requestAccessService) {

        this.service = service;
        this.requestAccessService = requestAccessService;
    }

    @PostMapping
    public CustomerFormResponse save(

            @Valid
            @RequestBody CustomerFormResponseDTO dto,

            Authentication authentication

    ) {

        return service.save(dto, authentication.getName());

    }

    @GetMapping("/{phoneNumber}")
    public List<CustomerFormResponse>
    getByPhoneNumber(
            @PathVariable String phoneNumber,

            Authentication authentication) {

        requestAccessService.requireCustomerPhone(phoneNumber, authentication);

        return service.getByPhoneNumber(
                phoneNumber
        );
    }
    @GetMapping("/autofill/{phoneNumber}")
    public Map<String, String> getAutoFill(
            @PathVariable String phoneNumber,
            Authentication authentication
    ) {
        requestAccessService.requireCustomerPhone(phoneNumber, authentication);
        return service.getAutoFillData(
                phoneNumber
        );
    }
}
