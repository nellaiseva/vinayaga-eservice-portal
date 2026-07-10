package com.eservice1.admin.controller;

import com.eservice1.service.entity.PortalService;
import com.eservice1.service.service.PortalServiceService;
import org.springframework.web.bind.annotation.*;
import com.eservice1.service.dto.CreateServiceRequest;
import jakarta.validation.Valid;
import java.util.List;
import com.eservice1.common.dto.PageResponseDTO;
@RestController
@RequestMapping("/admin/services")
public class AdminServiceController {

    private final PortalServiceService service;

    public AdminServiceController(PortalServiceService service) {
        this.service = service;
    }
    @PostMapping
    public PortalService createService(

            @Valid
            @RequestBody
            CreateServiceRequest request

    ) {

        return service.createService(request);

    }


    @GetMapping
    public PageResponseDTO<PortalService> getAllServices(

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size,

            @RequestParam(required = false) String search

    ) {

        return service.getAll(
                page,
                size,
                search
        );
    }
    @DeleteMapping("/{id}")
    public void deleteService(@PathVariable Long id) {
       // System.out.println("DELETE CONTROLLER HIT");

        service.delete(id);
    }
    @PutMapping("/{id}")
    public PortalService updateService(

            @PathVariable Long id,

            @Valid
            @RequestBody CreateServiceRequest request

    ) {

        PortalService serviceToUpdate =
                service.getById(id);

        serviceToUpdate.setServiceName(
                request.getServiceName()
        );

        serviceToUpdate.setDescription(
                request.getDescription()
        );

        serviceToUpdate.setActive(
                request.getActive()
        );

        return service.save(serviceToUpdate);

    }
    @GetMapping("/{id}")
    public PortalService getService(
            @PathVariable Long id) {

        return service.getById(id);

    }
}





























































































































































































































































































