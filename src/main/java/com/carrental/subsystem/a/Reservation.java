package com.carrental.subsystem.a;

import com.carrental.exception.InvalidTransitionException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain entity of Subsystem A. Holds its own status machine.
 * Transitions:
 *   NEW -> RESERVED
 *   NEW -> CANCELLED
 *   RESERVED -> CANCELLED
 * (no other transitions allowed)
 */
public class Reservation {
    private final String reservationId;
    private final String customerId;
    private final String carId;
    private final int rentalDays;
    private final double totalPrice;
    private ReservationStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Reservation(String reservationId, String customerId, String carId,
                       int rentalDays, double totalPrice) {
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.carId = carId;
        this.rentalDays = rentalDays;
        this.totalPrice = totalPrice;
        this.status = ReservationStatus.NEW;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** Full constructor used when reloading from persistence. */
    public Reservation(String reservationId, String customerId, String carId,
                       int rentalDays, double totalPrice,
                       ReservationStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.carId = carId;
        this.rentalDays = rentalDays;
        this.totalPrice = totalPrice;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void reserve() throws InvalidTransitionException {
        if (status != ReservationStatus.NEW) {
            throw new InvalidTransitionException(
                    "Cannot reserve: current status is " + status + ", expected NEW");
        }
        this.status = ReservationStatus.RESERVED;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() throws InvalidTransitionException {
        if (status == ReservationStatus.CANCELLED) {
            throw new InvalidTransitionException("Already cancelled");
        }
        this.status = ReservationStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    public String getReservationId() { return reservationId; }
    public String getCustomerId() { return customerId; }
    public String getCarId() { return carId; }
    public int getRentalDays() { return rentalDays; }
    public double getTotalPrice() { return totalPrice; }
    public ReservationStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reservation that)) return false;
        return Objects.equals(reservationId, that.reservationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId);
    }

    @Override
    public String toString() {
        return "Reservation{id='" + reservationId + "', customer='" + customerId
                + "', car='" + carId + "', days=" + rentalDays
                + ", price=" + totalPrice + ", status=" + status + "}";
    }
}
