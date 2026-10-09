package com.carrental.subsystem.c;

import com.carrental.exception.ResourceUnavailableException;
import com.carrental.exception.ValidationException;

/**
 * Pure business-logic of Subsystem C.
 * Simulates a pre-rental damage inspection.
 */
public class DamageInspector {

    private boolean forceFailure = false;

    public void setForceFailure(boolean forceFailure) {
        this.forceFailure = forceFailure;
    }

    public boolean isForceFailure() {
        return forceFailure;
    }

    /**
     * Returns true if the car is in acceptable condition.
     * Throws ResourceUnavailableException when forced failure is active
     * (used for live demo of C-failure).
     */
    public boolean inspect(String carId) throws ValidationException, ResourceUnavailableException {
        if (carId == null || carId.isBlank()) {
            throw new ValidationException("Car id required for inspection");
        }
        if (forceFailure) {
            forceFailure = false;
            throw new ResourceUnavailableException(
                    "Damage inspection station offline – cannot finalize rental for car " + carId);
        }
        // In a real system we would query sensors / photos.
        // For the assignment we always pass unless forced.
        return true;
    }
}
