-- =========================================================================
-- V5__customer_password_enrollment.sql
-- Customer password enrollment schema migration
-- =========================================================================

-- 1. Update OTP purpose check constraint to include customer enrollment and password reset
ALTER TABLE otp_verification
    DROP CONSTRAINT IF EXISTS otp_verification_purpose_check;

ALTER TABLE otp_verification
    ADD CONSTRAINT otp_verification_purpose_check
    CHECK (purpose IN (
        'CUSTOMER_LOGIN',
        'CUSTOMER_ENROLLMENT',
        'CUSTOMER_PASSWORD_RESET',
        'EMPLOYEE_PASSWORD_RESET'
    ));

-- 2. Add explicit customer_enrolled status flag to users table (default false)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS customer_enrolled BOOLEAN NOT NULL DEFAULT FALSE;
