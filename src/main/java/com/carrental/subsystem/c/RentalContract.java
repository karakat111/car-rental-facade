package com.carrental.subsystem.c;

import com.carrental.exception.InvalidTransitionException;

import java.time.LocalDateTime;
import java.util.Objects;

public class RentalContract {
    private final String contractId;
    private final String reservationId;
    private final String customerId;
    private final String carId;
    private final boolean damageOk;
    private final String gpsDeviceId;
    private ContractStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RentalContract(String contractId, String reservationId, String customerId,
                          String carId, boolean damageOk, String gpsDeviceId) {
        this.contractId = contractId;
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.carId = carId;
        this.damageOk = damageOk;
        this.gpsDeviceId = gpsDeviceId;
        this.status = ContractStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public RentalContract(String contractId, String reservationId, String customerId,
                          String carId, boolean damageOk, String gpsDeviceId,
                          ContractStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.contractId = contractId;
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.carId = carId;
        this.damageOk = damageOk;
        this.gpsDeviceId = gpsDeviceId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void activate() throws InvalidTransitionException {
        if (status != ContractStatus.DRAFT) {
            throw new InvalidTransitionException("Cannot activate: status is " + status);
        }
        this.status = ContractStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() throws InvalidTransitionException {
        if (status != ContractStatus.ACTIVE) {
            throw new InvalidTransitionException("Cannot complete: status is " + status);
        }
        this.status = ContractStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() throws InvalidTransitionException {
        if (status == ContractStatus.CANCELLED || status == ContractStatus.COMPLETED) {
            throw new InvalidTransitionException("Cannot cancel: status is " + status);
        }
        this.status = ContractStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    public String getContractId() { return contractId; }
    public String getReservationId() { return reservationId; }
    public String getCustomerId() { return customerId; }
    public String getCarId() { return carId; }
    public boolean isDamageOk() { return damageOk; }
    public String getGpsDeviceId() { return gpsDeviceId; }
    public ContractStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RentalContract that)) return false;
        return Objects.equals(contractId, that.contractId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contractId);
    }

    @Override
    public String toString() {
        return "RentalContract{id='" + contractId + "', res='" + reservationId
                + "', damageOk=" + damageOk + ", gps=" + gpsDeviceId + ", status=" + status + "}";
    }
}
