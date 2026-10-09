package com.carrental.facade;

import com.carrental.exception.CarRentalException;
import com.carrental.exception.SimulatedCrashException;
import com.carrental.exception.ValidationException;
import com.carrental.subsystem.a.Car;
import com.carrental.subsystem.a.Reservation;
import com.carrental.subsystem.a.ReservationService;
import com.carrental.subsystem.b.Deposit;
import com.carrental.subsystem.b.ProcessingService;
import com.carrental.subsystem.c.FinalizationService;
import com.carrental.subsystem.c.RentalContract;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main client-facing Facade.
 * Orchestrates the three independent subsystems A -> B -> C as one logical operation
 * and persists the chain progress, so that after a restart it can finish or undo
 * the work from what is really on disk.
 */
public class CarRentalFacade {

    private final ReservationService reservationService;   // A
    private final ProcessingService processingService;     // B
    private final FinalizationService finalizationService; // C
    private final ChainRepository chainRepository;
    private final ChainCompensator compensator;

    /** Demo/test hook: pretend the process is killed right after this status was persisted. */
    private ChainStatus crashAfter;

    public CarRentalFacade(ReservationService reservationService,
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

    /**
     * The single coherent operation a client calls.
     * Returns the chain record: COMPLETED, or COMPENSATED_A / COMPENSATED_B_A when a
     * later step failed and the facade already undid the right part.
     * Throws when the request is rejected before anything was committed
     * (invalid input, car not available) - the rejection is also recorded as REJECTED_A.
     */
    public RentalChainRecord rentCar(String customerId, String carId, int rentalDays,
                                     String coverageType) throws CarRentalException {

        // ---------- validation: nothing is written yet ----------
        if (customerId == null || customerId.isBlank()) {
            throw new ValidationException("customerId is required");
        }
        if (carId == null || carId.isBlank()) {
            throw new ValidationException("carId is required");
        }
        coverageType = (coverageType == null || coverageType.isBlank())
                ? "BASIC" : coverageType.trim().toUpperCase();
        processingService.validateCoverage(coverageType);

        String chainId = "CHN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        RentalChainRecord chain = new RentalChainRecord(chainId, customerId, carId,
                rentalDays, coverageType);
        chainRepository.save(chain); // PENDING_A

        // ---------- Step A ----------
        Reservation reservation;
        try {
            reservation = reservationService.createReservation(customerId, carId, rentalDays);
        } catch (CarRentalException e) {
            chain.markRejected(e.getMessage());   // A refused: nothing to undo
            chainRepository.save(chain);
            throw e;
        }
        chain.markPendingB(reservation.getReservationId());
        chainRepository.save(chain);
        crashPointReached(ChainStatus.PENDING_B);

        // ---------- Step B ----------
        try {
            Car car = reservationService.getFleet().get(carId);
            Deposit deposit = processingService.process(
                    reservation.getReservationId(), customerId, car.getCategory(),
                    rentalDays, reservation.getTotalPrice(), coverageType);
            chain.markPendingC(deposit.getDepositId());
            chainRepository.save(chain);
        } catch (CarRentalException e) {
            // B failed after A committed -> compensate A (and any half-written B data)
            compensator.compensateAfterBFailure(chain, e.getMessage());
            return chain;
        }
        crashPointReached(ChainStatus.PENDING_C);

        // ---------- Step C ----------
        RentalContract contract;
        try {
            contract = finalizationService.finalizeRental(
                    reservation.getReservationId(), customerId, carId);
        } catch (CarRentalException e) {
            // C failed after A and B committed -> compensate B, then A
            compensator.compensateBA(chain, e.getMessage());
            return chain;
        }
        chain.markCompleted(contract.getContractId());
        chainRepository.save(chain);
        return chain;
    }

    /**
     * Restart recovery. Reads the chain from disk and, if it is unfinished,
     * completes or compensates it according to what the subsystems have persisted.
     */
    public RentalChainRecord resumeOrInspect(String chainId) throws CarRentalException {
        RentalChainRecord chain = chainRepository.findById(chainId)
                .orElseThrow(() -> new CarRentalException("Chain not found: " + chainId));
        compensator.resume(chain, "Recovered after restart");
        return chainRepository.findById(chainId).orElse(chain);
    }

    /** Chains that a crash left in PENDING_A / PENDING_B / PENDING_C. */
    public List<RentalChainRecord> findUnfinishedChains() throws CarRentalException {
        return chainRepository.findAll().stream()
                .filter(RentalChainRecord::isUnfinished)
                .toList();
    }

    public List<RentalChainRecord> listAllChains() throws CarRentalException {
        return chainRepository.findAll();
    }

    public Map<String, Car> listFleet() {
        return reservationService.getFleet();
    }

    public boolean isCarAvailable(String carId) {
        return reservationService.isAvailable(carId);
    }

    /** Demo/test hook: the next rentCar "dies" right after the given status is persisted. */
    public void simulateCrashAfter(ChainStatus status) {
        this.crashAfter = status;
    }

    private void crashPointReached(ChainStatus status) {
        if (crashAfter == status) {
            crashAfter = null;
            throw new SimulatedCrashException("Process killed after " + status + " was persisted");
        }
    }
}
