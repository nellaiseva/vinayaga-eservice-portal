package com.eservice1.submission.repository;

import com.eservice1.submission.entity.PaymentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, Long> {

    /** Retrieve all audit entries for a specific request, ordered chronologically. */
    List<PaymentAuditLog> findByRequestIdOrderByPerformedAtAsc(Long requestId);
}
