package com.carrental.subsystem.b;

import com.carrental.exception.ResourceUnavailableException;
import com.carrental.exception.ValidationException;

/**
 * Business-logic class that decides whether a deposit can be held.
 * Simulates a payment-gateway capacity limit for demonstration of failures.
 */
public class DepositHandler {

    /** Card authorisation limit of the (simulated) payment gateway. */
    public static final double MAX_DEPOSIT = 1_000.0;

    /** When true, the next hold attempt will fail (for live demo of B-failure). */
    private boolean forceFailure = false;

    public void setForceFailure(boolean forceFailure) {
        this.forceFailure = forceFailure;
    }

    public boolean isForceFailure() {
        return forceFailure;
    }

    public void validateAndHold(double amount, String customerId)
            throws ValidationException, ResourceUnavailableException {
        if (amount <= 0) {
            throw new ValidationException("Deposit amount must be positive");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new ValidationException("Customer id required for deposit");
        }
        // Simulated capacity / gateway failure
        if (forceFailure) {
            forceFailure = false; // one-shot
            throw new ResourceUnavailableException(
                    "Payment gateway temporarily unavailable – cannot hold deposit of " + amount);
        }
        // Realistic failure: the gateway cannot authorise a deposit above the card limit
        if (amount > MAX_DEPOSIT) {
            throw new ResourceUnavailableException(
                    "Payment gateway declined: deposit " + amount
                            + " exceeds the authorisation limit " + MAX_DEPOSIT);
        }
        // In a real system we would call a payment provider here
    }
}
