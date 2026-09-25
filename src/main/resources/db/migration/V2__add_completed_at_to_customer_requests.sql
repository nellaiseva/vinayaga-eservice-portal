-- =========================================================================
-- V2__add_completed_at_to_customer_requests.sql
-- Add completed_at column to customer_requests table for file retention tracking
-- =========================================================================

ALTER TABLE customer_requests
ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP(6) WITHOUT TIME ZONE;
