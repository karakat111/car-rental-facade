package com.carrental.exception;

/**
 * Unchecked exception used only as a demo/test hook: it models the process
 * being killed in the middle of the chain (after a step was committed to disk).
 * It deliberately is NOT a CarRentalException, so the facade does not
 * "handle" it – exactly like a real crash cannot be handled.
 */
public class SimulatedCrashException extends RuntimeException {
    public SimulatedCrashException(String message) {
        super(message);
    }
}
