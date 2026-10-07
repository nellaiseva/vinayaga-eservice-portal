package com.eservice1.employee.controller;

import com.eservice1.admin.dto.AdminRequestDTO;
import com.eservice1.admin.dto.DashboardStatsDTO;
import com.eservice1.admin.service.AdminRequestService;
import com.eservice1.common.dto.PageResponseDTO;
import com.eservice1.employee.repository.EmployeeRepository;
import com.eservice1.submission.entity.RequestStatus;
import com.eservice1.submission.repository.CustomerRequestRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/employee/requests")
public class EmployeeRequestController {

    private final AdminRequestService adminRequestService;
    private final CustomerRequestRepository requestRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeRequestController(
            AdminRequestService adminRequestService,
            CustomerRequestRepository requestRepository,
            EmployeeRepository employeeRepository
    ) {
        this.adminRequestService = adminRequestService;
        this.requestRepository = requestRepository;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping
    public PageResponseDTO<AdminRequestDTO> getAllRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String status,
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

    @GetMapping("/stats")
    public DashboardStatsDTO getStats() {

        long totalRequests = requestRepository.count();

        long totalEmployees = employeeRepository.count();

        long pendingRequests =
                requestRepository.countByStatus(RequestStatus.PENDING);

        long assignedRequests =
                requestRepository.countByStatus(RequestStatus.ASSIGNED);

        long inProgressRequests =
                requestRepository.countByStatus(RequestStatus.IN_PROGRESS);

        long completedRequests =
                requestRepository.countByStatus(RequestStatus.COMPLETED);

        return new DashboardStatsDTO(
                totalRequests,
                totalEmployees,
                pendingRequests,
                assignedRequests,
                inProgressRequests,
                completedRequests
        );
    }
}
