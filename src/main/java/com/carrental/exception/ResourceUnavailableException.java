package com.carrental.exception;

/**
 * Thrown when a required resource (car, insurance capacity, GPS device…)
 * is not available at the moment of the request.
 */
public class ResourceUnavailableException extends CarRentalException {
    public ResourceUnavailableException(String message) {
        super(message);
    }
}
