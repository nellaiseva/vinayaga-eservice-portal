package com.eservice1.submission.service;

import com.eservice1.common.exception.InvalidOperationException;
import com.eservice1.common.exception.ResourceNotFoundException;
import com.eservice1.service.entity.PortalService;
import com.eservice1.service.repository.PortalServiceRepository;
import com.eservice1.submission.dto.CustomerRequestDTO;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.entity.PaymentAuditLog;
import com.eservice1.submission.entity.PaymentStatus;
import com.eservice1.submission.entity.RequestStatus;
import com.eservice1.submission.repository.CustomerRequestRepository;
import com.eservice1.submission.repository.PaymentAuditLogRepository;
import org.springframework.stereotype.Service;

import com.eservice1.employee.entity.Priority;
import com.eservice1.employee.entity.Task;
import com.eservice1.employee.entity.TaskStatus;
import com.eservice1.employee.repository.TaskRepository;
import java.time.LocalDateTime;
import com.eservice1.admin.dto.AdminRequestDTO;
import com.eservice1.common.dto.PageResponseDTO;
import com.eservice1.common.util.PaginationMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.eservice1.feedback.repository.FeedbackRepository;
import com.eservice1.submission.dto.CustomerRequestViewDTO;
import com.eservice1.customer.entity.CustomerProfile;
import com.eservice1.customer.repository.CustomerProfileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerRequestService {

    private final CustomerRequestRepository requestRepository;
    private final PortalServiceRepository serviceRepository;

    private final TaskRepository taskRepository;
    private final FeedbackRepository feedbackRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final RequestAccessService requestAccessService;
    private final PaymentAuditLogRepository paymentAuditLogRepository;

    public CustomerRequestService(
            CustomerRequestRepository requestRepository,
            PortalServiceRepository serviceRepository,
            TaskRepository taskRepository,
            FeedbackRepository feedbackRepository,
            CustomerProfileRepository customerProfileRepository,
            RequestAccessService requestAccessService,
            PaymentAuditLogRepository paymentAuditLogRepository) {

        this.requestRepository = requestRepository;
        this.serviceRepository = serviceRepository;
        this.taskRepository = taskRepository;
        this.feedbackRepository = feedbackRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.requestAccessService = requestAccessService;
        this.paymentAuditLogRepository = paymentAuditLogRepository;
    }

    @Transactional
    public CustomerRequest createRequest(
            CustomerRequestDTO dto,
            String authenticatedPhoneNumber) {

        CustomerProfile customerProfile = customerProfileRepository
                .findByPhoneNumber(authenticatedPhoneNumber);

        if (customerProfile == null
                || customerProfile.getCustomerName() == null
                || customerProfile.getCustomerName().isBlank()) {
            throw new InvalidOperationException(
                    "Complete your customer profile before creating a request."
            );
        }

        PortalService service =
                serviceRepository.findById(
                                dto.getServiceId()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Service not found."
                                )
                        );
        if (!Boolean.TRUE.equals(service.getActive())) {

            throw new InvalidOperationException(
                    "This service is currently unavailable."
            );

        }

        CustomerRequest request = new CustomerRequest();

        request.setCustomerName(customerProfile.getCustomerName());
        request.setPhoneNumber(authenticatedPhoneNumber);
        request.setService(service);
        request.setStatus(RequestStatus.PENDING);
        request.setPaymentStatus(
                PaymentStatus.UNPAID
        );

        request.setAmount(0.0);
        request.setPaymentDate(null);

        request.setCreatedAt(
                LocalDateTime.now()
        );

        CustomerRequest savedRequest =
                requestRepository.save(request);

        Task task = new Task();

        task.setRequest(savedRequest);
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(Priority.MEDIUM);


        taskRepository.save(task);

        return savedRequest;
    }

    /**
     * Secure payment update with state-machine enforcement, audit logging,
     * and optimistic-locking support.
     *
     * <p>Authorization (enforced by {@link RequestAccessService#requirePaymentAccess}):
     * <ul>
     *   <li>OWNER — may perform any transition</li>
     *   <li>Assigned EMPLOYEE — may mark UNPAID → PAID only</li>
     *   <li>CUSTOMER — access denied (blocked at SecurityConfig before reaching here)</li>
     * </ul>
     *
     * <p>State-machine rules:
     * <ol>
     *   <li>UNPAID → PAID: allowed for OWNER or assigned EMPLOYEE; amount must be > 0 and ≤ 10,000,000</li>
     *   <li>PAID → PAID: rejected (duplicate payment guard)</li>
     *   <li>PAID → UNPAID: OWNER only (admin reversal)</li>
     *   <li>UNPAID → UNPAID: no-op, rejected with an error (caller should not do this)</li>
     * </ol>
     *
     * <p>Every state change is written to {@code payment_audit_logs} in the same transaction.
     */
    @Transactional
    public CustomerRequest updatePayment(
            Long requestId,
            PaymentStatus newStatus,
            Double amount,
            Authentication authentication
    ) {
        // 1. Authorization: OWNER or assigned EMPLOYEE only
        CustomerRequest request = requestAccessService.requirePaymentAccess(
                requestId,
                authentication
        );

        PaymentStatus currentStatus = request.getPaymentStatus();
        boolean isOwner = authentication.getAuthorities().stream()
                .anyMatch(a -> "OWNER".equals(a.getAuthority()));

        // 2. State-machine validation
        if (currentStatus == PaymentStatus.PAID && newStatus == PaymentStatus.PAID) {
            throw new InvalidOperationException(
                    "This request has already been marked as paid. Duplicate payment update rejected."
            );
        }

        if (currentStatus == PaymentStatus.PAID && newStatus == PaymentStatus.UNPAID) {
            if (!isOwner) {
                throw new AccessDeniedException(
                        "Only the OWNER can reverse a payment back to UNPAID."
                );
            }
        }

        if (currentStatus == PaymentStatus.UNPAID && newStatus == PaymentStatus.UNPAID) {
            throw new InvalidOperationException(
                    "Request is already UNPAID. No state change occurred."
            );
        }

        // 3. Amount validation (required when marking PAID)
        if (newStatus == PaymentStatus.PAID) {
            if (amount == null || amount <= 0) {
                throw new InvalidOperationException(
                        "Amount must be greater than zero."
                );
            }
            if (amount > 10_000_000) {
                throw new InvalidOperationException(
                        "Amount exceeds the maximum permitted value."
                );
            }
        }

        // 4. Apply state change
        request.setPaymentStatus(newStatus);

        if (newStatus == PaymentStatus.PAID) {
            request.setAmount(amount);
            request.setPaymentDate(LocalDateTime.now());
        } else {
            // UNPAID reversal: clear the amount and date
            request.setAmount(0.0);
            request.setPaymentDate(null);
        }

        CustomerRequest saved = requestRepository.save(request);

        // 5. Write audit log in the same transaction
        PaymentAuditLog auditLog = new PaymentAuditLog();
        auditLog.setRequestId(requestId);
        auditLog.setPreviousStatus(currentStatus);
        auditLog.setNewStatus(newStatus);
        auditLog.setAmount(newStatus == PaymentStatus.PAID ? amount : null);
        auditLog.setPerformedBy(authentication.getName());
        auditLog.setPerformedAt(LocalDateTime.now());
        if (currentStatus == PaymentStatus.PAID && newStatus == PaymentStatus.UNPAID) {
            auditLog.setNotes("Admin reversal by OWNER");
        }
        paymentAuditLogRepository.save(auditLog);

        return saved;
    }

    public PageResponseDTO<AdminRequestDTO> getAllRequests(

            int page,

            int size

    ) {

        Pageable pageable =

                PageRequest.of(

                        page,

                        size,

                        Sort.by("createdAt").descending()

                );

        Page<CustomerRequest> requests =

                requestRepository.findAll(pageable);

        Page<AdminRequestDTO> dtoPage =

                requests.map(request -> {

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

                    Task task =
                            taskRepository.findByRequestId(
                                    request.getId()
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

                });

        return PaginationMapper.toResponse(
                dtoPage
        );

    }

    public PageResponseDTO<CustomerRequestViewDTO> getRequests(

            String phoneNumber,

            int page,

            int size,

            Authentication authentication

    ) {

        requestAccessService.requireCustomerPhone(phoneNumber, authentication);

        Pageable pageable =

                PageRequest.of(

                        page,

                        size,

                        Sort.by("createdAt").descending()

                );

        Page<CustomerRequest> requests =

                requestRepository.findByPhoneNumberOrderByCreatedAtDesc(

                        phoneNumber,

                        pageable

                );

        Page<CustomerRequestViewDTO> dtoPage =

                requests.map(request -> {

                    CustomerRequestViewDTO dto =

                            new CustomerRequestViewDTO();

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

                    dto.setFeedbackSubmitted(

                            feedbackRepository.existsByRequestId(

                                    request.getId()

                            )

                    );

                    return dto;

                });

        return PaginationMapper.toResponse(

                dtoPage

        );

    }
}
