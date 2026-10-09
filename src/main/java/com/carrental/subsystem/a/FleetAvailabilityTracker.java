package com.carrental.subsystem.a;

import com.carrental.exception.ResourceUnavailableException;
import com.carrental.exception.ValidationException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Business-logic + in-memory availability map for Subsystem A.
 * Tracks which cars are currently free. The real durable state of
 * reservations lives in ReservationRepository; this class only answers
 * "is this car free right now?".
 */
public class FleetAvailabilityTracker {

    private final Map<String, Boolean> availability = new HashMap<>();

    public void registerCar(Car car) {
        availability.putIfAbsent(car.getCarId(), true);
    }

    public boolean isAvailable(String carId) {
        return availability.getOrDefault(carId, false);
    }

    public void markReserved(String carId) throws ResourceUnavailableException {
        if (!isAvailable(carId)) {
            throw new ResourceUnavailableException("Car " + carId + " is not available");
        }
        availability.put(carId, false);
    }

    /** Used when the in-memory cache is rebuilt from the persisted reservations. */
    public void markUnavailable(String carId) {
        availability.put(carId, false);
    }

    public void markReleased(String carId) {
        availability.put(carId, true);
    }

    public Optional<Car> findFirstAvailableOfCategory(String category, Map<String, Car> fleet)
            throws ResourceUnavailableException {
        Optional<Car> found = fleet.values().stream()
                .filter(c -> c.getCategory().equalsIgnoreCase(category))
                .filter(c -> isAvailable(c.getCarId()))
                .findFirst();
        if (found.isEmpty()) {
            throw new ResourceUnavailableException(
                    "No available car of category " + category);
        }
        return found;
    }

    public void validateCarExists(String carId, Map<String, Car> fleet)
            throws ValidationException {
        if (!fleet.containsKey(carId)) {
            throw new ValidationException("Unknown car id: " + carId);
        }
    }
}
