package com.eservice1.customer.repository;

import com.eservice1.customer.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpVerificationRepository
        extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification>
    findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
    long countByPhoneNumberAndCreatedAtAfter(
            String phoneNumber,
            LocalDateTime createdAt
    );
    void deleteByPhoneNumber(String phoneNumber);
}