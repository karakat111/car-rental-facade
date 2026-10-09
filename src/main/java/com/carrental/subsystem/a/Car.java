package com.carrental.subsystem.a;

import java.util.Objects;

/**
 * Immutable value object representing a vehicle in the fleet.
 */
public final class Car {
    private final String carId;
    private final String model;
    private final String category; // ECONOMY, COMPACT, SUV, LUXURY
    private final double dailyRate;

    public Car(String carId, String model, String category, double dailyRate) {
        if (carId == null || carId.isBlank()) {
            throw new IllegalArgumentException("carId must not be blank");
        }
        this.carId = carId;
        this.model = model;
        this.category = category;
        this.dailyRate = dailyRate;
    }

    public String getCarId() { return carId; }
    public String getModel() { return model; }
    public String getCategory() { return category; }
    public double getDailyRate() { return dailyRate; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Car car)) return false;
        return Objects.equals(carId, car.carId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carId);
    }

    @Override
    public String toString() {
        return "Car{id='" + carId + "', model='" + model + "', category='" + category + "', rate=" + dailyRate + "}";
    }
}
