package com.eservice1.submission.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Append-only audit record for every payment state change on a CustomerRequest.
 * One row is written per transition; rows are never updated or deleted by the
 * application so they form a tamper-evident log within normal operational use.
 */
@Entity
@Table(name = "payment_audit_logs")
public class PaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the affected customer request. */
    @Column(nullable = false)
    private Long requestId;

    /** Payment status before the change. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus previousStatus;

    /** Payment status after the change. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus newStatus;

    /** Amount recorded at the time of this change (may be null for UNPAID). */
    private Double amount;

    /** Phone number (identity) of the authenticated actor who performed the change. */
    @Column(nullable = false)
    private String performedBy;

    /** Timestamp of the change (set server-side, never from client). */
    @Column(nullable = false)
    private LocalDateTime performedAt;

    /** Optional free-text note (e.g. "admin reversal"). Populated by the server only. */
    private String notes;

    public PaymentAuditLog() {
    }

    // ---- Getters ----

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public PaymentStatus getPreviousStatus() {
        return previousStatus;
    }

    public PaymentStatus getNewStatus() {
        return newStatus;
    }

    public Double getAmount() {
        return amount;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public LocalDateTime getPerformedAt() {
        return performedAt;
    }

    public String getNotes() {
        return notes;
    }

    // ---- Setters ----

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public void setPreviousStatus(PaymentStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public void setNewStatus(PaymentStatus newStatus) {
        this.newStatus = newStatus;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public void setPerformedAt(LocalDateTime performedAt) {
        this.performedAt = performedAt;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
