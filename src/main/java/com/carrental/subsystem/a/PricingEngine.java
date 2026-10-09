package com.carrental.subsystem.a;

import com.carrental.exception.ValidationException;

/**
 * Pure business-logic class of Subsystem A.
 * Calculates the total rental price based on car daily rate and duration.
 * No persistence knowledge.
 */
public class PricingEngine {

    /**
     * Applies a simple weekend surcharge (10 %) when rentalDays >= 3
     * and a loyalty discount of 5 % for known customers (customerId starts with "LOYAL-").
     */
    public double calculateTotalPrice(Car car, int rentalDays, String customerId)
            throws ValidationException {
        if (car == null) {
            throw new ValidationException("Car must not be null");
        }
        if (rentalDays < 1 || rentalDays > 90) {
            throw new ValidationException("Rental days must be between 1 and 90");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new ValidationException("Customer id must not be blank");
        }

        double base = car.getDailyRate() * rentalDays;
        if (rentalDays >= 3) {
            base *= 1.10; // weekend / long-rental surcharge
        }
        if (customerId.startsWith("LOYAL-")) {
            base *= 0.95; // loyalty discount
        }
        return Math.round(base * 100.0) / 100.0;
    }
}
