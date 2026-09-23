package com.eservice1.admin.controller;

import com.eservice1.admin.dto.DashboardStatsDTO;
import com.eservice1.employee.repository.EmployeeRepository;
import com.eservice1.submission.entity.RequestStatus;
import com.eservice1.submission.repository.CustomerRequestRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasAuthority('OWNER')")
public class AdminDashboardController {

    private final CustomerRequestRepository requestRepository;
    private final EmployeeRepository employeeRepository;

    public AdminDashboardController(
            CustomerRequestRepository requestRepository,
            EmployeeRepository employeeRepository) {

        this.requestRepository = requestRepository;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/stats")
    public DashboardStatsDTO getStats() {

        long totalRequests =
                requestRepository.count();

        long totalEmployees =
                employeeRepository.count();

        long pendingRequests =
                requestRepository.countByStatus(
                        RequestStatus.PENDING
                );

        long assignedRequests =
                requestRepository.countByStatus(
                        RequestStatus.ASSIGNED
                );

        long inProgressRequests =
                requestRepository.countByStatus(
                        RequestStatus.IN_PROGRESS
                );

        long completedRequests =
                requestRepository.countByStatus(
                        RequestStatus.COMPLETED
                );

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