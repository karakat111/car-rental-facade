package com.carrental.subsystem.b;

import com.carrental.exception.ValidationException;

/**
 * Pure business-logic class of Subsystem B.
 * Calculates insurance premium based on car category and coverage.
 */
public class InsuranceCalculator {

    public double calculatePremium(String carCategory, int rentalDays, String coverageType)
            throws ValidationException {
        if (carCategory == null || carCategory.isBlank()) {
            throw new ValidationException("Car category required");
        }
        if (rentalDays < 1) {
            throw new ValidationException("Rental days must be positive");
        }
        if (coverageType == null || coverageType.isBlank()) {
            throw new ValidationException("Coverage type required");
        }

        double dailyBase = switch (carCategory.toUpperCase()) {
            case "ECONOMY" -> 8.0;
            case "COMPACT" -> 12.0;
            case "SUV" -> 18.0;
            case "LUXURY" -> 30.0;
            default -> 10.0;
        };

        double multiplier = multiplierFor(coverageType);

        return Math.round(dailyBase * rentalDays * multiplier * 100.0) / 100.0;
    }

    /** Throws if the coverage type is not one of BASIC / FULL / PREMIUM. */
    public void validateCoverage(String coverageType) throws ValidationException {
        if (coverageType == null || coverageType.isBlank()) {
            throw new ValidationException("Coverage type required");
        }
        multiplierFor(coverageType);
    }

    private double multiplierFor(String coverageType) throws ValidationException {
        return switch (coverageType.toUpperCase()) {
            case "BASIC" -> 1.0;
            case "FULL" -> 1.6;
            case "PREMIUM" -> 2.2;
            default -> throw new ValidationException("Unknown coverage: " + coverageType);
        };
    }

    public double calculateDepositAmount(String carCategory, double rentalPrice) {
        // Deposit is roughly 20-40 % of rental price depending on category
        double factor = switch (carCategory.toUpperCase()) {
            case "ECONOMY" -> 0.20;
            case "COMPACT" -> 0.25;
            case "SUV" -> 0.30;
            case "LUXURY" -> 0.40;
            default -> 0.25;
        };
        return Math.round(rentalPrice * factor * 100.0) / 100.0;
    }
}
