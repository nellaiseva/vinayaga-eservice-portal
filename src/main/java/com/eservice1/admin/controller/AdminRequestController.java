package com.eservice1.admin.controller;

import com.eservice1.common.exception.ResourceNotFoundException;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.repository.CustomerRequestRepository;
import org.springframework.web.bind.annotation.*;
import com.eservice1.admin.dto.AdminRequestDTO;
import com.eservice1.admin.service.AdminRequestService;
import com.eservice1.common.dto.PageResponseDTO;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/admin/requests")
@PreAuthorize("hasAuthority('OWNER')")
public class AdminRequestController {

    private final AdminRequestService
            adminRequestService;

    private final CustomerRequestRepository
            requestRepository;public AdminRequestController(

            AdminRequestService adminRequestService,

            CustomerRequestRepository requestRepository

    ) {

        this.adminRequestService =
                adminRequestService;

        this.requestRepository =
                requestRepository;

    }

    @GetMapping
    public PageResponseDTO<AdminRequestDTO> getAllRequests(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            String phone,

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date

    ) {

        return adminRequestService.getAllRequests(

                page,

                size,

                search,

                phone,

                status,

                date

        );
    }
    @GetMapping("/{id}")
    public CustomerRequest getRequest(
            @PathVariable Long id) {

        return requestRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Request not found."
                        )
                );

    }

    @GetMapping("/phone/{phoneNumber}")
    public java.util.List<CustomerRequest>
    getByPhoneNumber(
            @PathVariable String phoneNumber) {

        return requestRepository
                .findByPhoneNumberOrderByCreatedAtDesc(
                        phoneNumber

                );
    }


}