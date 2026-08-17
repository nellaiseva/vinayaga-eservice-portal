package com.eservice1.customer.repository;

import com.eservice1.customer.entity.OtpPurpose;
import com.eservice1.customer.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface OtpVerificationRepository
        extends JpaRepository<OtpVerification, Long> {

    // Existing customer OTP logic — DO NOT CHANGE
    Optional<OtpVerification>
    findTopByPhoneNumberOrderByCreatedAtDesc(
            String phoneNumber
    );

    long countByPhoneNumberAndCreatedAtAfter(
            String phoneNumber,
            Instant createdAt
    );

    // Employee password reset OTP logic
    Optional<OtpVerification>
    findTopByPhoneNumberAndPurposeOrderByCreatedAtDesc(
            String phoneNumber,
            OtpPurpose purpose
    );

    long countByPhoneNumberAndPurposeAndCreatedAtAfter(
            String phoneNumber,
            OtpPurpose purpose,
            Instant createdAt
    );
}