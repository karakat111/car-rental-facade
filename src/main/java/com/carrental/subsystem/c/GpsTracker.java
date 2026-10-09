package com.carrental.subsystem.c;

import com.carrental.exception.ResourceUnavailableException;
import com.carrental.exception.ValidationException;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Business-logic + inventory of GPS devices.
 * The pool is small on purpose: once all devices are out on the road the next
 * rental cannot be finalised - a realistic way for step C to fail.
 * Which devices are out is rebuilt from the persisted contracts after a restart.
 */
public class GpsTracker {

    public static final int DEFAULT_POOL_SIZE = 3;

    private final Set<String> freeDevices = new TreeSet<>();
    private final Set<String> assignedDevices = new TreeSet<>();

    public GpsTracker() {
        this(DEFAULT_POOL_SIZE);
    }

    public GpsTracker(int poolSize) {
        for (int i = 1; i <= poolSize; i++) {
            freeDevices.add("GPS-" + String.format("%03d", i));
        }
    }

    public String assignDevice(String carId) throws ValidationException, ResourceUnavailableException {
        if (carId == null || carId.isBlank()) {
            throw new ValidationException("Car id required");
        }
        if (freeDevices.isEmpty()) {
            throw new ResourceUnavailableException("No free GPS devices left");
        }
        String device = freeDevices.iterator().next();
        freeDevices.remove(device);
        assignedDevices.add(device);
        return device;
    }

    public void releaseDevice(String deviceId) {
        if (assignedDevices.remove(deviceId)) {
            freeDevices.add(deviceId);
        }
    }

    /** Marks devices that persisted contracts still use as assigned (restart recovery). */
    public void restoreAssigned(Collection<String> deviceIds) {
        for (String id : deviceIds) {
            if (freeDevices.remove(id)) {
                assignedDevices.add(id);
            }
        }
    }

    public int freeCount() {
        return freeDevices.size();
    }
}
