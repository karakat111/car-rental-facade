package com.carrental.facade;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Durable record of a full rental chain. Stored independently so that
 * after a restart the facade can resume or compensate correctly.
 */
public class RentalChainRecord {
    private final String chainId;
    private final String customerId;
    private final String carId;
    private final int rentalDays;
    private final String coverageType;
    private String reservationId;
    private String depositId;
    private String contractId;
    private ChainStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String lastError;

    public RentalChainRecord(String chainId, String customerId, String carId,
                             int rentalDays, String coverageType) {
        this.chainId = chainId;
        this.customerId = customerId;
        this.carId = carId;
        this.rentalDays = rentalDays;
        this.coverageType = coverageType;
        this.status = ChainStatus.PENDING_A;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public RentalChainRecord(String chainId, String customerId, String carId,
                             int rentalDays, String coverageType,
                             String reservationId, String depositId, String contractId,
                             ChainStatus status, LocalDateTime createdAt, LocalDateTime updatedAt,
                             String lastError) {
        this.chainId = chainId;
        this.customerId = customerId;
        this.carId = carId;
        this.rentalDays = rentalDays;
        this.coverageType = coverageType;
        this.reservationId = reservationId;
        this.depositId = depositId;
        this.contractId = contractId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastError = lastError;
    }

    public void markPendingB(String reservationId) {
        this.reservationId = reservationId;
        this.status = ChainStatus.PENDING_B;
        this.updatedAt = LocalDateTime.now();
    }

    public void markPendingC(String depositId) {
        this.depositId = depositId;
        this.status = ChainStatus.PENDING_C;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCompleted(String contractId) {
        this.contractId = contractId;
        this.status = ChainStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCompensatedA(String error) {
        this.status = ChainStatus.COMPENSATED_A;
        this.lastError = error;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCompensatedBA(String error) {
        this.status = ChainStatus.COMPENSATED_B_A;
        this.lastError = error;
        this.updatedAt = LocalDateTime.now();
    }

    public void markRejected(String error) {
        this.status = ChainStatus.REJECTED_A;
        this.lastError = error;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isUnfinished() {
        return status == ChainStatus.PENDING_A
                || status == ChainStatus.PENDING_B
                || status == ChainStatus.PENDING_C;
    }

    // getters
    public String getChainId() { return chainId; }
    public String getCustomerId() { return customerId; }
    public String getCarId() { return carId; }
    public int getRentalDays() { return rentalDays; }
    public String getCoverageType() { return coverageType; }
    public String getReservationId() { return reservationId; }
    public String getDepositId() { return depositId; }
    public String getContractId() { return contractId; }
    public ChainStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getLastError() { return lastError; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RentalChainRecord that)) return false;
        return Objects.equals(chainId, that.chainId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chainId);
    }

    @Override
    public String toString() {
        return "RentalChainRecord{id='" + chainId + "', status=" + status
                + ", res=" + reservationId + ", dep=" + depositId + ", ctr=" + contractId
                + (lastError != null ? ", error='" + lastError + "'" : "") + "}";
    }
}
