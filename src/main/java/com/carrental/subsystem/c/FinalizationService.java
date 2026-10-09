package com.carrental.subsystem.c;

import com.carrental.exception.CarRentalException;

import java.util.Optional;
import java.util.UUID;

/**
 * Public entry point of Subsystem C. Usable standalone.
 */
public class FinalizationService {

    private final DamageInspector damageInspector;
    private final GpsTracker gpsTracker;
    private final ContractRepository repository;

    public FinalizationService(DamageInspector damageInspector,
                               GpsTracker gpsTracker,
                               ContractRepository repository) {
        this.damageInspector = damageInspector;
        this.gpsTracker = gpsTracker;
        this.repository = repository;
        rehydrateGpsAssignments();
    }

    /** After a restart the GPS pool must reflect the contracts that are on disk. */
    private void rehydrateGpsAssignments() {
        try {
            gpsTracker.restoreAssigned(repository.findActiveGpsDevices());
        } catch (CarRentalException e) {
            throw new IllegalStateException("Cannot restore GPS assignments from store", e);
        }
    }

    public RentalContract finalizeRental(String reservationId, String customerId, String carId)
            throws CarRentalException {

        boolean damageOk = damageInspector.inspect(carId);
        String gpsId = gpsTracker.assignDevice(carId);

        String contractId = "CTR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        RentalContract contract = new RentalContract(contractId, reservationId, customerId,
                carId, damageOk, gpsId);
        contract.activate();
        repository.save(contract);
        return contract;
    }

    /** Compensation: cancel the contract and release the GPS device. Idempotent. */
    public void compensate(String reservationId) throws CarRentalException {
        Optional<RentalContract> found = repository.findByReservation(reservationId);
        if (found.isPresent()) {
            RentalContract c = found.get();
            if (c.getStatus() == ContractStatus.ACTIVE || c.getStatus() == ContractStatus.DRAFT) {
                c.cancel();
                repository.save(c);
                if (c.getGpsDeviceId() != null) {
                    gpsTracker.releaseDevice(c.getGpsDeviceId());
                }
            }
        }
    }

    /** Non-throwing lookup used by the facades to inspect what is really on disk. */
    public Optional<RentalContract> find(String reservationId) throws CarRentalException {
        return repository.findByReservation(reservationId);
    }

    public RentalContract findByReservation(String reservationId) throws CarRentalException {
        return repository.findByReservation(reservationId)
                .orElseThrow(() -> new CarRentalException("No contract for " + reservationId));
    }

    public void setForceFailure(boolean value) {
        damageInspector.setForceFailure(value);
    }

    public void reset() throws CarRentalException {
        repository.deleteAll();
        damageInspector.setForceFailure(false);
    }
}
