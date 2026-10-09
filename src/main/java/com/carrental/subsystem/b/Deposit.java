package com.carrental.subsystem.b;

import com.carrental.exception.InvalidTransitionException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain entity of Subsystem B – security deposit.
 * Transitions: PENDING -> HELD -> REFUNDED | FORFEITED
 */
public class Deposit {
    private final String depositId;
    private final String reservationId;
    private final String customerId;
    private final double amount;
    private DepositStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Deposit(String depositId, String reservationId, String customerId, double amount) {
        this.depositId = depositId;
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = DepositStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Deposit(String depositId, String reservationId, String customerId, double amount,
                   DepositStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.depositId = depositId;
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void hold() throws InvalidTransitionException {
        if (status != DepositStatus.PENDING) {
            throw new InvalidTransitionException("Cannot hold: status is " + status);
        }
        this.status = DepositStatus.HELD;
        this.updatedAt = LocalDateTime.now();
    }

    public void refund() throws InvalidTransitionException {
        if (status != DepositStatus.HELD) {
            throw new InvalidTransitionException("Cannot refund: status is " + status);
        }
        this.status = DepositStatus.REFUNDED;
        this.updatedAt = LocalDateTime.now();
    }

    public void forfeit() throws InvalidTransitionException {
        if (status != DepositStatus.HELD) {
            throw new InvalidTransitionException("Cannot forfeit: status is " + status);
        }
        this.status = DepositStatus.FORFEITED;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDepositId() { return depositId; }
    public String getReservationId() { return reservationId; }
    public String getCustomerId() { return customerId; }
    public double getAmount() { return amount; }
    public DepositStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Deposit d)) return false;
        return Objects.equals(depositId, d.depositId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(depositId);
    }

    @Override
    public String toString() {
        return "Deposit{id='" + depositId + "', res='" + reservationId
                + "', amount=" + amount + ", status=" + status + "}";
    }
}
