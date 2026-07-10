package com.eservice1.admin.service;

import com.eservice1.admin.dto.AdminRequestDTO;
import com.eservice1.employee.entity.Task;
import com.eservice1.employee.repository.TaskRepository;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.repository.CustomerRequestRepository;
import org.springframework.stereotype.Service;
import com.eservice1.common.dto.PageResponseDTO;
import com.eservice1.common.util.PaginationMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import com.eservice1.submission.specification.CustomerRequestSpecification;
import org.springframework.data.jpa.domain.Specification;
@Service
public class AdminRequestService {

    private final CustomerRequestRepository requestRepository;

    private final TaskRepository taskRepository;

    public AdminRequestService(
            CustomerRequestRepository requestRepository,
            TaskRepository taskRepository
    ) {

        this.requestRepository = requestRepository;
        this.taskRepository = taskRepository;

    }

    public PageResponseDTO<AdminRequestDTO> getAllRequests(

            int page,

            int size,

            String search,

            String phone,

            String status,

            LocalDate date

    ) {
        if (status != null) {
            status = status.trim();

            if (status.equalsIgnoreCase("ALL") || status.isBlank()) {
                status = null;
            }
        }

        if (search != null) {
            search = search.trim();

            if (search.isBlank()) {
                search = null;
            }
        }

        if (phone != null) {
            phone = phone.trim();

            if (phone.isBlank()) {
                phone = null;
            }
        }
        Pageable pageable =

                PageRequest.of(

                        page,

                        size,

                        Sort.by("createdAt").descending()

                );

        Specification<CustomerRequest> specification =
                CustomerRequestSpecification.filter(
                        search,
                        phone,
                        status,
                        date
                );

        Page<CustomerRequest> requests =
                requestRepository.findAll(
                        specification,
                        pageable
                );

        List<Long> requestIds =

                requests.getContent()
                        .stream()
                        .map(CustomerRequest::getId)
                        .toList();

        List<Task> tasks =

                taskRepository.findByRequestIdIn(
                        requestIds
                );

        java.util.Map<Long, Task> taskMap =

                tasks.stream()
                        .collect(

                                java.util.stream.Collectors.toMap(

                                        task ->
                                                task.getRequest().getId(),

                                        task -> task

                                )

                        );

        Page<AdminRequestDTO> dtoPage =

                requests.map(request ->

                        convertToDTO(

                                request,

                                taskMap.get(
                                        request.getId()
                                )

                        )

                );

        return PaginationMapper.toResponse(
                dtoPage
        );
    }
    private AdminRequestDTO convertToDTO(

            CustomerRequest request,

            Task task

    ) {

        AdminRequestDTO dto =
                new AdminRequestDTO();

        dto.setId(
                request.getId()
        );

        dto.setCustomerName(
                request.getCustomerName()
        );

        dto.setPhoneNumber(
                request.getPhoneNumber()
        );

        dto.setServiceName(
                request.getService().getServiceName()
        );

        dto.setStatus(
                request.getStatus().name()
        );

        dto.setCreatedAt(
                request.getCreatedAt()
        );



        if (
                task != null &&
                        task.getEmployee() != null
        ) {

            dto.setAssignedEmployeeId(
                    task.getEmployee().getId()
            );

            dto.setAssignedEmployeeName(
                    task.getEmployee().getName()
            );

        }

        return dto;

    }

}