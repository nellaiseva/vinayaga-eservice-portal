-- Migration: Add document_type column to uploaded_documents table
-- Supports multiple documents of the same required document type for a single service request.

ALTER TABLE uploaded_documents ADD COLUMN IF NOT EXISTS document_type VARCHAR(255);

-- For backward compatibility with existing single-document uploads,
-- set document_type to document_name where document_type is null.
UPDATE uploaded_documents SET document_type = document_name WHERE document_type IS NULL;
