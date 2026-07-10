package com.eservice1.submission.repository;

import com.eservice1.submission.entity.CustomerRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import com.eservice1.submission.entity.PaymentStatus;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.eservice1.submission.entity.RequestStatus;import com.eservice1.submission.repository.CustomerRequestRepository;

public interface CustomerRequestRepository
        extends JpaRepository<CustomerRequest, Long>,
        JpaSpecificationExecutor<CustomerRequest> {
    List<CustomerRequest>
    findByPhoneNumberOrderByCreatedAtDesc(
            String phoneNumber
    );
    Page<CustomerRequest>
    findByPhoneNumberOrderByCreatedAtDesc(

            String phoneNumber,

            Pageable pageable

    );
    long countByCreatedAtBetween(
            LocalDateTime start,
            LocalDateTime end
    );
    long countByPaymentStatus(
            PaymentStatus paymentStatus
    );long countByStatus(RequestStatus status);
    boolean existsByService_Id(Long serviceId);
    @Query("""
SELECT COALESCE(SUM(c.amount),0)
FROM CustomerRequest c
WHERE c.paymentStatus='PAID'
""")
    Double getTotalRevenue();

    @Query("""
SELECT
c.service.serviceName,
COUNT(c)
FROM CustomerRequest c
GROUP BY c.service.serviceName
""")
    List<Object[]>
    getServiceRequestCounts();




}
