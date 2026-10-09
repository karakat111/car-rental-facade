package com.carrental.exception;

/**
 * Thrown for invalid input data (negative days, empty customer id, etc.).
 */
public class ValidationException extends CarRentalException {
    public ValidationException(String message) {
        super(message);
    }
}
