package com.carrental.exception;

/**
 * Thrown when an entity tries to move to a status that is not allowed
 * from its current status.
 */
public class InvalidTransitionException extends CarRentalException {
    public InvalidTransitionException(String message) {
        super(message);
    }
}
