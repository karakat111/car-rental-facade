package com.carrental.subsystem.a;

import com.carrental.exception.CarRentalException;
import com.carrental.exception.ResourceUnavailableException;
import com.carrental.exception.ValidationException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Public entry point of Subsystem A. Can be used standalone.
 * Coordinates PricingEngine, FleetAvailabilityTracker and ReservationRepository.
 */
public class ReservationService {

    private final PricingEngine pricingEngine;
    private final FleetAvailabilityTracker fleetTracker;
    private final ReservationRepository repository;
    private final Map<String, Car> fleet = new HashMap<>();

    public ReservationService(PricingEngine pricingEngine,
                              FleetAvailabilityTracker fleetTracker,
                              ReservationRepository repository) {
        this.pricingEngine = pricingEngine;
        this.fleetTracker = fleetTracker;
        this.repository = repository;
        seedFleet();
        rehydrateAvailability();
    }

    /**
     * Availability is a cache; the source of truth is the database.
     * After a restart every car that still has a RESERVED reservation on disk
     * must be unavailable again (otherwise a car could be double-booked).
     */
    private void rehydrateAvailability() {
        try {
            for (String carId : repository.findReservedCarIds()) {
                fleetTracker.markUnavailable(carId);
            }
        } catch (CarRentalException e) {
            throw new IllegalStateException("Cannot restore fleet availability from store", e);
        }
    }

    private void seedFleet() {
        register(new Car("CAR-001", "Toyota Corolla", "ECONOMY", 35.0));
        register(new Car("CAR-002", "Honda Civic", "COMPACT", 42.0));
        register(new Car("CAR-003", "Ford Explorer", "SUV", 65.0));
        register(new Car("CAR-004", "BMW 5-Series", "LUXURY", 120.0));
        register(new Car("CAR-005", "Tesla Model 3", "COMPACT", 55.0));
    }

    private void register(Car car) {
        fleet.put(car.getCarId(), car);
        fleetTracker.registerCar(car);
    }

    public boolean isAvailable(String carId) {
        return fleetTracker.isAvailable(carId);
    }

    public java.util.Optional<Reservation> findActiveReservation(String customerId, String carId)
            throws CarRentalException {
        return repository.findActive(customerId, carId);
    }

    public Map<String, Car> getFleet() {
        return Map.copyOf(fleet);
    }

    /**
     * Creates a NEW reservation, marks the car as reserved and persists it.
     */
    public Reservation createReservation(String customerId, String carId, int rentalDays)
            throws CarRentalException {
        fleetTracker.validateCarExists(carId, fleet);
        Car car = fleet.get(carId);
        double price = pricingEngine.calculateTotalPrice(car, rentalDays, customerId);

        fleetTracker.markReserved(carId);

        String id = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Reservation reservation = new Reservation(id, customerId, carId, rentalDays, price);
        reservation.reserve(); // NEW -> RESERVED
        repository.save(reservation);
        return reservation;
    }

    /**
     * Compensation method: cancels an already RESERVED reservation and releases the car.
     */
    public void cancelReservation(String reservationId) throws CarRentalException {
        Reservation r = repository.findById(reservationId)
                .orElseThrow(() -> new CarRentalException("Reservation not found: " + reservationId));
        r.cancel();
        fleetTracker.markReleased(r.getCarId());
        repository.save(r);
    }

    public Reservation findById(String id) throws CarRentalException {
        return repository.findById(id)
                .orElseThrow(() -> new CarRentalException("Reservation not found: " + id));
    }

    public void reset() throws CarRentalException {
        repository.deleteAll();
        // re-seed availability
        fleet.values().forEach(c -> fleetTracker.markReleased(c.getCarId()));
    }
}
