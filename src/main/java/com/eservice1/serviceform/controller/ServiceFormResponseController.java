package com.eservice1.serviceform.controller;

import com.eservice1.serviceform.dto.ServiceFormResponseDTO;
import com.eservice1.serviceform.entity.ServiceFormResponse;
import com.eservice1.serviceform.service.ServiceFormResponseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.eservice1.serviceform.dto.ServiceFormResponseViewDTO;
import com.eservice1.submission.service.RequestAccessService;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/service-form-responses")
public class ServiceFormResponseController {

    private final ServiceFormResponseService service;
    private final RequestAccessService requestAccessService;

    public ServiceFormResponseController(
            ServiceFormResponseService service,
            RequestAccessService requestAccessService
    ) {
        this.service = service;
        this.requestAccessService = requestAccessService;
    }

    @PostMapping
    public void save(

            @RequestBody
            List<@Valid ServiceFormResponseDTO> responses,
            Authentication authentication

    ) {

        responses.forEach(response -> requestAccessService.requireCustomerRequestAccess(
                response.getRequestId(),
                authentication
        ));

        service.saveResponses(responses);
    }

    @GetMapping("/{requestId}")
    public List<ServiceFormResponse> get(
            @PathVariable Long requestId,
            Authentication authentication
    ) {
        requestAccessService.requireRequestAccess(requestId, authentication);
        return service.getResponses(
                requestId
        );
    }
    @GetMapping("/request/{requestId}")
    public List<ServiceFormResponseViewDTO>
    getResponseDetails(
            @PathVariable Long requestId,
            Authentication authentication
    ) {

        requestAccessService.requireRequestAccess(requestId, authentication);

        return service.getResponseDetails(
                requestId
        );
    }
}
