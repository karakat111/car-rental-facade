package com.carrental.subsystem.b;

import com.carrental.exception.InvalidTransitionException;

import java.time.LocalDateTime;
import java.util.Objects;

public class InsurancePolicy {
    private final String policyId;
    private final String reservationId;
    private final String customerId;
    private final double premium;
    private final String coverageType; // BASIC, FULL, PREMIUM
    private InsuranceStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public InsurancePolicy(String policyId, String reservationId, String customerId,
                           double premium, String coverageType) {
        this.policyId = policyId;
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.premium = premium;
        this.coverageType = coverageType;
        this.status = InsuranceStatus.QUOTED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public InsurancePolicy(String policyId, String reservationId, String customerId,
                           double premium, String coverageType,
                           InsuranceStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.policyId = policyId;
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.premium = premium;
        this.coverageType = coverageType;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void activate() throws InvalidTransitionException {
        if (status != InsuranceStatus.QUOTED) {
            throw new InvalidTransitionException("Cannot activate: status is " + status);
        }
        this.status = InsuranceStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() throws InvalidTransitionException {
        if (status == InsuranceStatus.CANCELLED) {
            throw new InvalidTransitionException("Already cancelled");
        }
        this.status = InsuranceStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    public String getPolicyId() { return policyId; }
    public String getReservationId() { return reservationId; }
    public String getCustomerId() { return customerId; }
    public double getPremium() { return premium; }
    public String getCoverageType() { return coverageType; }
    public InsuranceStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InsurancePolicy that)) return false;
        return Objects.equals(policyId, that.policyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyId);
    }

    @Override
    public String toString() {
        return "InsurancePolicy{id='" + policyId + "', res='" + reservationId
                + "', premium=" + premium + ", type=" + coverageType + ", status=" + status + "}";
    }
}
