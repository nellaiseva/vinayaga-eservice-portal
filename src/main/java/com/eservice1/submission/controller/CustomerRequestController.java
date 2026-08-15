package com.eservice1.submission.controller;

import com.eservice1.common.dto.PageResponseDTO;
import com.eservice1.submission.dto.CustomerRequestDTO;
import com.eservice1.submission.dto.CustomerRequestViewDTO;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.service.CustomerRequestService;
import com.eservice1.submission.service.RequestAccessService;
import org.springframework.web.bind.annotation.*;
import com.eservice1.submission.entity.PaymentStatus;import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
@RestController
@RequestMapping("/requests")
public class CustomerRequestController {

    private final CustomerRequestService requestService;
    private final RequestAccessService requestAccessService;

    public CustomerRequestController(
            CustomerRequestService requestService,
            RequestAccessService requestAccessService) {

        this.requestService = requestService;
        this.requestAccessService = requestAccessService;
    }

    @PostMapping
    public CustomerRequest createRequest(
            @Valid
            @RequestBody CustomerRequestDTO dto,
            Authentication authentication) {

        return requestService.createRequest(dto, authentication.getName());
    }
    @GetMapping("/phone/{phoneNumber}")
    public PageResponseDTO<CustomerRequestViewDTO> getByPhoneNumber(

            @PathVariable String phoneNumber,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size,

            Authentication authentication

    ) {

        return requestService.getRequests(

                phoneNumber,

                page,

                size,

                authentication

        );

    }
    @GetMapping("/{id}")
    public CustomerRequest getRequest(
            @PathVariable Long id,
            Authentication authentication
    ) {

        return requestAccessService.requireRequestAccess(id, authentication);
    }
    @PostMapping("/{id}/payment")
    public CustomerRequest updatePayment(

            @PathVariable Long id,

            @RequestParam PaymentStatus status,

            @RequestParam Double amount,

            Authentication authentication

    ) {

        return requestService.updatePayment(
                id,
                status,
                amount,
                authentication
        );

    }

}

