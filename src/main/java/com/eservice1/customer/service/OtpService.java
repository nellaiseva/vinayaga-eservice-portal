package com.eservice1.customer.service;

import com.eservice1.config.JwtService;
import com.eservice1.customer.dto.OtpResponse;
import com.eservice1.customer.dto.SendOtpRequest;
import com.eservice1.customer.dto.VerifyOtpRequest;
import com.eservice1.customer.entity.OtpVerification;
import com.eservice1.customer.repository.OtpVerificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OtpService {

    private final OtpVerificationRepository repository;
    private final JwtService jwtService;

    public OtpService(OtpVerificationRepository repository,
                      JwtService jwtService) {

        this.repository = repository;
        this.jwtService = jwtService;
    }

    public OtpResponse sendOtp(SendOtpRequest request) {

        String phoneNumber = request.getPhoneNumber();

        long otpCount = repository.countByPhoneNumberAndCreatedAtAfter(
                phoneNumber,
                LocalDateTime.now().minusHours(1)
        );

        if (otpCount >= 5) {

            return new OtpResponse(
                    false,
                    "Maximum OTP requests reached. Please try again after one hour.",
                    null
            );
        }

        Optional<OtpVerification> existing =
                repository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber);

        if (existing.isPresent()) {

            OtpVerification oldOtp = existing.get();

            if (LocalDateTime.now().isBefore(
                    oldOtp.getCreatedAt().plusSeconds(60))) {

                long seconds =
                        java.time.Duration.between(
                                LocalDateTime.now(),
                                oldOtp.getCreatedAt().plusSeconds(60)
                        ).getSeconds();

                return new OtpResponse(
                        false,
                        "Please wait " + seconds + " seconds before requesting another OTP.",
                        null
                );
            }

            repository.delete(oldOtp);
        }

        String otp = String.valueOf(
                ThreadLocalRandom.current()
                        .nextInt(100000, 1000000)
        );

        OtpVerification verification = new OtpVerification();

        verification.setPhoneNumber(phoneNumber);
        verification.setOtp(otp);
        verification.setCreatedAt(LocalDateTime.now());
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        verification.setVerified(false);
        verification.setAttempts(0);

        repository.save(verification);

        System.out.println("--------------------------------");
        System.out.println("PHONE : " + phoneNumber);
        System.out.println("OTP   : " + otp);
        System.out.println("--------------------------------");

        return new OtpResponse(
                true,
                "OTP sent successfully",
                null
        );
    }

    public OtpResponse verifyOtp(
            VerifyOtpRequest request) {

        Optional<OtpVerification> optional =
                repository.findTopByPhoneNumberOrderByCreatedAtDesc(
                        request.getPhoneNumber()
                );

        if (optional.isEmpty()) {

            return new OtpResponse(
                    false,
                    "OTP not found",
                    null
            );
        }

        OtpVerification verification =
                optional.get();

        if (verification.isVerified()) {

            return new OtpResponse(
                    false,
                    "OTP already used",
                    null
            );
        }

        if (LocalDateTime.now().isAfter(
                verification.getExpiresAt())) {

            return new OtpResponse(
                    false,
                    "OTP expired",
                    null
            );
        }

        if (verification.getAttempts() >= 5) {

            return new OtpResponse(
                    false,
                    "Maximum attempts exceeded",
                    null
            );
        }

        if (!verification.getOtp()
                .equals(request.getOtp())) {

            verification.setAttempts(
                    verification.getAttempts() + 1
            );

            repository.save(verification);

            return new OtpResponse(
                    false,
                    "Invalid OTP",
                    null
            );
        }

        repository.delete(verification);

        String token =
                jwtService.generateToken(
                        request.getPhoneNumber()
                );

        return new OtpResponse(
                true,
                "OTP Verified",
                token
        );
    }
}