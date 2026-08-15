package com.eservice1.submission.service;

import com.eservice1.common.exception.ResourceNotFoundException;
import com.eservice1.employee.entity.Employee;
import com.eservice1.employee.entity.Task;
import com.eservice1.employee.repository.EmployeeRepository;
import com.eservice1.employee.repository.TaskRepository;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.repository.CustomerRequestRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class RequestAccessService {

    private final CustomerRequestRepository requestRepository;
    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;

    public RequestAccessService(
            CustomerRequestRepository requestRepository,
            TaskRepository taskRepository,
            EmployeeRepository employeeRepository) {

        this.requestRepository = requestRepository;
        this.taskRepository = taskRepository;
        this.employeeRepository = employeeRepository;
    }

    public CustomerRequest requireRequestAccess(
            Long requestId,
            Authentication authentication) {

        CustomerRequest request = requestRepository.findById(requestId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Request not found."));

        if (hasAuthority(authentication, "OWNER")
                || isRequestCustomer(request, authentication)
                || isAssignedEmployee(request, authentication)) {
            return request;
        }

        throw new AccessDeniedException("You are not authorized to access this request.");
    }

    public CustomerRequest requirePaymentAccess(
            Long requestId,
            Authentication authentication) {

        CustomerRequest request = requestRepository.findById(requestId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Request not found."));

        if (hasAuthority(authentication, "OWNER")
                || isAssignedEmployee(request, authentication)) {
            return request;
        }

        throw new AccessDeniedException("You are not authorized to update this payment.");
    }

    public CustomerRequest requireCustomerRequestAccess(
            Long requestId,
            Authentication authentication) {

        CustomerRequest request = requestRepository.findById(requestId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Request not found."));

        if (isRequestCustomer(request, authentication)) {
            return request;
        }

        throw new AccessDeniedException("You are not authorized to modify this request.");
    }

    public void requireReceiptUploadAccess(
            Long taskId,
            Authentication authentication) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found."));

        if (hasAuthority(authentication, "OWNER")
                || isAssignedEmployee(task.getRequest(), authentication)) {
            return;
        }

        throw new AccessDeniedException("You are not authorized to upload this receipt.");
    }

    public void requireCustomerPhone(
            String phoneNumber,
            Authentication authentication) {

        if (!hasAuthority(authentication, "CUSTOMER")
                || !Objects.equals(authentication.getName(), phoneNumber)) {
            throw new AccessDeniedException("You are not authorized to access this customer data.");
        }
    }

    public void requireResultDocumentUploadAccess(
            Long requestId,
            Authentication authentication) {

        CustomerRequest request = requireRequestAccess(requestId, authentication);

        if (!hasAuthority(authentication, "OWNER")
                && !isAssignedEmployee(request, authentication)) {
            throw new AccessDeniedException("You are not authorized to upload a result document.");
        }
    }

    private boolean isRequestCustomer(
            CustomerRequest request,
            Authentication authentication) {

        return hasAuthority(authentication, "CUSTOMER")
                && Objects.equals(request.getPhoneNumber(), authentication.getName());
    }

    private boolean isAssignedEmployee(
            CustomerRequest request,
            Authentication authentication) {

        if (!hasAuthority(authentication, "EMPLOYEE")) {
            return false;
        }

        Employee employee = employeeRepository.findByPhoneNumber(authentication.getName());
        Task task = taskRepository.findByRequestId(request.getId());

        return employee != null
                && task != null
                && task.getEmployee() != null
                && Objects.equals(task.getEmployee().getId(), employee.getId());
    }

    private boolean hasAuthority(Authentication authentication, String authority) {

        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }
}
