package com.carrental.subsystem.b;

import com.carrental.exception.CarRentalException;
import com.carrental.exception.ValidationException;

import java.util.Optional;
import java.util.UUID;

/**
 * Public entry point of Subsystem B. Usable standalone.
 */
public class ProcessingService {

    private final InsuranceCalculator calculator;
    private final DepositHandler depositHandler;
    private final DepositRepository repository;

    public ProcessingService(InsuranceCalculator calculator,
                             DepositHandler depositHandler,
                             DepositRepository repository) {
        this.calculator = calculator;
        this.depositHandler = depositHandler;
        this.repository = repository;
    }

    /**
     * Processes insurance + deposit for an already-existing reservation.
     * Returns the created Deposit (insurance is also persisted).
     */
    public Deposit process(String reservationId, String customerId,
                           String carCategory, int rentalDays, double rentalPrice,
                           String coverageType) throws CarRentalException {

        double premium = calculator.calculatePremium(carCategory, rentalDays, coverageType);
        double depositAmount = calculator.calculateDepositAmount(carCategory, rentalPrice);

        // May throw ResourceUnavailableException (forced failure for demo)
        depositHandler.validateAndHold(depositAmount, customerId);

        String policyId = "INS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        InsurancePolicy policy = new InsurancePolicy(policyId, reservationId, customerId,
                premium, coverageType);
        policy.activate();
        repository.saveInsurance(policy);

        String depositId = "DEP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Deposit deposit = new Deposit(depositId, reservationId, customerId, depositAmount);
        deposit.hold();
        repository.saveDeposit(deposit);

        return deposit;
    }

    /**
     * Compensation: refund the deposit and cancel the insurance policy.
     * Idempotent - it only touches records that are still HELD / not CANCELLED.
     */
    public void compensate(String reservationId) throws CarRentalException {
        Optional<Deposit> deposit = repository.findDepositByReservation(reservationId);
        if (deposit.isPresent() && deposit.get().getStatus() == DepositStatus.HELD) {
            Deposit d = deposit.get();
            d.refund();
            repository.saveDeposit(d);
        }
        Optional<InsurancePolicy> policy = repository.findInsuranceByReservation(reservationId);
        if (policy.isPresent() && policy.get().getStatus() != InsuranceStatus.CANCELLED) {
            InsurancePolicy p = policy.get();
            p.cancel();
            repository.saveInsurance(p);
        }
    }

    /** True when B has persisted anything (deposit or insurance) for this reservation. */
    public boolean hasRecords(String reservationId) throws CarRentalException {
        return repository.findDepositByReservation(reservationId).isPresent()
                || repository.findInsuranceByReservation(reservationId).isPresent();
    }

    /** Lets a caller reject an unknown coverage type before anything is committed. */
    public void validateCoverage(String coverageType) throws ValidationException {
        calculator.validateCoverage(coverageType);
    }

    public InsurancePolicy findInsurance(String reservationId) throws CarRentalException {
        return repository.findInsuranceByReservation(reservationId)
                .orElseThrow(() -> new CarRentalException("No insurance for " + reservationId));
    }

    public Deposit findDeposit(String reservationId) throws CarRentalException {
        return repository.findDepositByReservation(reservationId)
                .orElseThrow(() -> new CarRentalException("No deposit for " + reservationId));
    }

    public void setForceFailure(boolean value) {
        depositHandler.setForceFailure(value);
    }

    public void reset() throws CarRentalException {
        repository.deleteAll();
        depositHandler.setForceFailure(false);
    }
}
