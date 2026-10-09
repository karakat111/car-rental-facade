package com.carrental.exception;

/**
 * Base exception for all car-rental domain errors.
 */
public class CarRentalException extends Exception {
    public CarRentalException(String message) {
        super(message);
    }

    public CarRentalException(String message, Throwable cause) {
        super(message, cause);
    }
}
