package com.carrental.facade;

import com.carrental.exception.CarRentalException;
import com.carrental.subsystem.a.Reservation;
import com.carrental.subsystem.a.ReservationService;
import com.carrental.subsystem.b.ProcessingService;
import com.carrental.subsystem.c.ContractStatus;
import com.carrental.subsystem.c.FinalizationService;
import com.carrental.subsystem.c.RentalContract;

import java.util.Optional;

/**
 * The single place that knows HOW to undo a prefix of the chain.
 * Both facades use it, so the rules (order, depth, "decide from disk") exist once.
 *
 *   B failed after A      -> compensateA        (status COMPENSATED_A)
 *   C failed after A + B  -> compensateBA       (B first, then A; COMPENSATED_B_A)
 *
 * The decision of how deep to go after a restart is never taken from memory:
 * it is taken from what the subsystems really have on disk.
 */
public class ChainCompensator {

    private final ReservationService reservations;   // A
    private final ProcessingService processing;      // B
    private final FinalizationService finalization;  // C
    private final ChainRepository chains;

    public ChainCompensator(ReservationService reservations,
                            ProcessingService processing,
                            FinalizationService finalization,
                            ChainRepository chains) {
        this.reservations = reservations;
        this.processing = processing;
        this.finalization = finalization;
        this.chains = chains;
    }

    /** Depth 1: undo A only. */
    public void compensateA(RentalChainRecord chain, String reason) throws CarRentalException {
        reservations.cancelReservation(chain.getReservationId());
        chain.markCompensatedA(reason);
        chains.save(chain);
    }

    /** Depth 2: undo B, then A (the order is mandatory). */
    public void compensateBA(RentalChainRecord chain, String reason) throws CarRentalException {
        processing.compensate(chain.getReservationId());
        reservations.cancelReservation(chain.getReservationId());
        chain.markCompensatedBA(reason);
        chains.save(chain);
    }

    /**
     * B reported a failure. Normally nothing of B is on disk (depth 1), but if B
     * managed to persist something before failing, that must be undone too.
     */
    public void compensateAfterBFailure(RentalChainRecord chain, String reason) throws CarRentalException {
        if (processing.hasRecords(chain.getReservationId())) {
            compensateBA(chain, reason);
        } else {
            compensateA(chain, reason);
        }
    }

    /**
     * Recovery after a restart. Looks at what is on disk:
     *  - C already persisted an ACTIVE contract -> the chain really finished: roll forward;
     *  - otherwise roll back exactly the part that exists.
     */
    public void resume(RentalChainRecord chain, String reason) throws CarRentalException {
        if (!chain.isUnfinished()) {
            return;
        }
        if (chain.getStatus() == ChainStatus.PENDING_A && !adoptOrphanReservation(chain)) {
            chain.markRejected(reason + " - nothing had been committed");
            chains.save(chain);
            return;
        }
        Optional<RentalContract> contract = finalization.find(chain.getReservationId());
        if (contract.isPresent() && contract.get().getStatus() == ContractStatus.ACTIVE) {
            if (chain.getDepositId() == null && processing.hasRecords(chain.getReservationId())) {
                chain.markPendingC(processing.findDeposit(chain.getReservationId()).getDepositId());
            }
            chain.markCompleted(contract.get().getContractId());
            chains.save(chain);
            return;
        }
        rollbackFromDisk(chain, reason);
    }

    /** Administrator-triggered rollback: always undoes everything that is on disk. */
    public void forceRollback(RentalChainRecord chain, String reason) throws CarRentalException {
        if (chain.getStatus() == ChainStatus.PENDING_A && !adoptOrphanReservation(chain)) {
            chain.markRejected(reason + " - nothing had been committed");
            chains.save(chain);
            return;
        }
        rollbackFromDisk(chain, reason);
    }

    private void rollbackFromDisk(RentalChainRecord chain, String reason) throws CarRentalException {
        String reservationId = chain.getReservationId();
        finalization.compensate(reservationId);          // no-op unless C persisted something
        if (processing.hasRecords(reservationId)) {
            compensateBA(chain, reason);
        } else {
            compensateA(chain, reason);
        }
    }

    /**
     * Crash window: A committed the reservation but the chain row still says PENDING_A.
     * The reservation is found again through the customer + car and attached to the chain.
     */
    private boolean adoptOrphanReservation(RentalChainRecord chain) throws CarRentalException {
        Optional<Reservation> orphan =
                reservations.findActiveReservation(chain.getCustomerId(), chain.getCarId());
        if (orphan.isEmpty()) {
            return false;
        }
        chain.markPendingB(orphan.get().getReservationId());
        chains.save(chain);
        return true;
    }
}
