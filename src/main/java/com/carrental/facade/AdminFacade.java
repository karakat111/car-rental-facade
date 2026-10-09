package com.carrental.facade;

import com.carrental.exception.CarRentalException;
import com.carrental.subsystem.a.Reservation;
import com.carrental.subsystem.a.ReservationService;
import com.carrental.subsystem.b.Deposit;
import com.carrental.subsystem.b.ProcessingService;
import com.carrental.subsystem.c.FinalizationService;
import com.carrental.subsystem.c.RentalContract;

import java.util.List;

/**
 * Narrower administrative Facade: inspection, manual compensation,
 * failure injection for the live demo and store reset.
 */
public class AdminFacade {

    private final ReservationService reservationService;
    private final ProcessingService processingService;
    private final FinalizationService finalizationService;
    private final ChainRepository chainRepository;
    private final ChainCompensator compensator;

    public AdminFacade(ReservationService reservationService,
                       ProcessingService processingService,
                       FinalizationService finalizationService,
                       ChainRepository chainRepository) {
        this.reservationService = reservationService;
        this.processingService = processingService;
        this.finalizationService = finalizationService;
        this.chainRepository = chainRepository;
        this.compensator = new ChainCompensator(reservationService, processingService,
                finalizationService, chainRepository);
    }

    public List<RentalChainRecord> inspectAllChains() throws CarRentalException {
        return chainRepository.findAll();
    }

    public Reservation inspectReservation(String reservationId) throws CarRentalException {
        return reservationService.findById(reservationId);
    }

    public Deposit inspectDeposit(String reservationId) throws CarRentalException {
        return processingService.findDeposit(reservationId);
    }

    public RentalContract inspectContract(String reservationId) throws CarRentalException {
        return finalizationService.findByReservation(reservationId);
    }

    /**
     * Manual compensation of a stuck (PENDING_*) chain. How deep to go is decided from
     * what is persisted: contract -> deposit/insurance -> reservation.
     * Finished chains are left alone; a COMPLETED chain cannot be compensated here.
     */
    public RentalChainRecord manualCompensate(String chainId) throws CarRentalException {
        RentalChainRecord chain = chainRepository.findById(chainId)
                .orElseThrow(() -> new CarRentalException("Chain not found: " + chainId));

        if (chain.getStatus() == ChainStatus.COMPLETED) {
            throw new CarRentalException(
                    "Cannot compensate a completed chain - use the normal business process");
        }
        if (chain.isUnfinished()) {
            compensator.forceRollback(chain, "Manual compensation by administrator");
        }
        return chainRepository.findById(chainId).orElse(chain);
    }

    // ---- failure injection for the live demo ----

    public void forceBFailureNext(boolean value) {
        processingService.setForceFailure(value);
    }

    public void forceCFailureNext(boolean value) {
        finalizationService.setForceFailure(value);
    }

    /** Clears all tables and restores fleet availability. */
    public void resetAll() throws CarRentalException {
        reservationService.reset();
        processingService.reset();
        finalizationService.reset();
        chainRepository.deleteAll();
    }
}
