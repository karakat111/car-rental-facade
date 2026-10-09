package com.carrental.subsystem.a;

/**
 * Lifecycle of a car reservation (Subsystem A).
 * Legal transitions are enforced by the Reservation entity itself.
 */
public enum ReservationStatus {
    NEW,
    RESERVED,
    CANCELLED
}
