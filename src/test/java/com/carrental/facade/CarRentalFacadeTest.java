package com.carrental.facade;

import com.carrental.exception.*;
import com.carrental.subsystem.a.*;
import com.carrental.subsystem.b.*;
import com.carrental.subsystem.c.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F.I.R.S.T.: every test gets its own fresh SQLite file (@TempDir, per-method lifecycle).
 * The helper methods form a small domain-specific testing language.
 */
class CarRentalFacadeTest {

    @TempDir
    Path tempDir;

    private String dbUrl;
    private CarRentalFacade facade;
    private AdminFacade admin;
    private ReservationService a;
    private ProcessingService b;
    private FinalizationService c;
    private ChainRepository chains;

    @BeforeEach
    void setUp() {
        dbUrl = "jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath();
        wire();
    }

    private void wire() {
        chains = new ChainRepository(dbUrl);
        a = new ReservationService(new PricingEngine(), new FleetAvailabilityTracker(),
                new ReservationRepository(dbUrl));
        b = new ProcessingService(new InsuranceCalculator(), new DepositHandler(),
                new DepositRepository(dbUrl));
        c = new FinalizationService(new DamageInspector(), new GpsTracker(),
                new ContractRepository(dbUrl));
        facade = new CarRentalFacade(a, b, c, chains);
        admin = new AdminFacade(a, b, c, chains);
    }

    // ---------------- DSL ----------------

    private RentalChainRecord aCustomerRents(String customer, String car, int days, String coverage)
            throws CarRentalException {
        return facade.rentCar(customer, car, days, coverage);
    }

    private void theDepositGatewayIsBroken() { admin.forceBFailureNext(true); }

    private void theDamageStationIsOffline() { admin.forceCFailureNext(true); }

    private void theStoreIsRestarted() { wire(); }   // new objects, same file on disk

    private RentalChainRecord theProcessIsKilledAfter(ChainStatus status, String customer, String car)
            throws CarRentalException {
        facade.simulateCrashAfter(status);
        assertThrows(SimulatedCrashException.class, () -> aCustomerRents(customer, car, 2, "BASIC"));
        return facade.findUnfinishedChains().get(0);
    }

    private Reservation reservationOf(RentalChainRecord chain) throws CarRentalException {
        return a.findById(chain.getReservationId());
    }

    // ---------------- happy path ----------------

    @Test
    void happyPath_resultsInCompletedChain() throws Exception {
        RentalChainRecord chain = aCustomerRents("CUST-001", "CAR-001", 3, "full");

        assertEquals(ChainStatus.COMPLETED, chain.getStatus());
        assertEquals(ReservationStatus.RESERVED, reservationOf(chain).getStatus());
        assertEquals(DepositStatus.HELD, b.findDeposit(chain.getReservationId()).getStatus());
        assertEquals(ContractStatus.ACTIVE, c.findByReservation(chain.getReservationId()).getStatus());
        assertEquals(ChainStatus.COMPLETED, chains.findById(chain.getChainId()).orElseThrow().getStatus());
    }

    // ---------------- compensation depth 1: B fails after A ----------------

    @Test
    void whenDepositGatewayFails_onlyReservationIsCompensated() throws Exception {
        theDepositGatewayIsBroken();

        RentalChainRecord chain = aCustomerRents("CUST-002", "CAR-002", 2, "BASIC");

        assertEquals(ChainStatus.COMPENSATED_A, chain.getStatus());
        assertEquals(ReservationStatus.CANCELLED, reservationOf(chain).getStatus());
        assertFalse(b.hasRecords(chain.getReservationId()), "B must have written nothing");
        assertTrue(a.isAvailable("CAR-002"), "car must be released");
        assertEquals(ChainStatus.COMPENSATED_A, chains.findById(chain.getChainId()).orElseThrow().getStatus());
    }

    @Test
    void whenDepositExceedsCardLimit_bFailsForRealAndOnlyAIsCompensated() throws Exception {
        RentalChainRecord chain = aCustomerRents("CUST-002", "CAR-004", 30, "BASIC");

        assertEquals(ChainStatus.COMPENSATED_A, chain.getStatus());
        assertTrue(chain.getLastError().contains("limit"));
    }

    // ---------------- compensation depth 2: C fails after A and B ----------------

    @Test
    void whenDamageStationFails_depositAndReservationAreCompensated() throws Exception {
        theDamageStationIsOffline();

        RentalChainRecord chain = aCustomerRents("CUST-003", "CAR-003", 5, "PREMIUM");

        assertEquals(ChainStatus.COMPENSATED_B_A, chain.getStatus());
        String res = chain.getReservationId();
        assertEquals(DepositStatus.REFUNDED, b.findDeposit(res).getStatus());
        assertEquals(InsuranceStatus.CANCELLED, b.findInsurance(res).getStatus());
        assertEquals(ReservationStatus.CANCELLED, reservationOf(chain).getStatus());
        assertTrue(c.find(res).isEmpty(), "C must have written nothing");
        assertTrue(a.isAvailable("CAR-003"));
    }

    @Test
    void whenGpsPoolIsExhausted_cFailsForRealAndBThenAAreCompensated() throws Exception {
        aCustomerRents("CUST-1", "CAR-001", 1, "BASIC");
        aCustomerRents("CUST-2", "CAR-002", 1, "BASIC");
        aCustomerRents("CUST-3", "CAR-003", 1, "BASIC");

        RentalChainRecord fourth = aCustomerRents("CUST-4", "CAR-004", 1, "BASIC");

        assertEquals(ChainStatus.COMPENSATED_B_A, fourth.getStatus());
        assertEquals(DepositStatus.REFUNDED, b.findDeposit(fourth.getReservationId()).getStatus());
    }

    // ---------------- persistence reload mid-chain ----------------

    @Test
    void afterRestart_chainStuckInPendingB_isCompensatedAlone() throws Exception {
        RentalChainRecord stuck = theProcessIsKilledAfter(ChainStatus.PENDING_B, "CUST-5", "CAR-001");
        theStoreIsRestarted();

        assertEquals(ChainStatus.PENDING_B, chains.findById(stuck.getChainId()).orElseThrow().getStatus());
        RentalChainRecord after = facade.resumeOrInspect(stuck.getChainId());

        assertEquals(ChainStatus.COMPENSATED_A, after.getStatus());
        assertEquals(ReservationStatus.CANCELLED, reservationOf(after).getStatus());
        assertTrue(a.isAvailable("CAR-001"));
    }

    @Test
    void afterRestart_chainStuckInPendingC_isCompensatedBThenA() throws Exception {
        RentalChainRecord stuck = theProcessIsKilledAfter(ChainStatus.PENDING_C, "CUST-5", "CAR-001");
        theStoreIsRestarted();

        RentalChainRecord after = facade.resumeOrInspect(stuck.getChainId());

        assertEquals(ChainStatus.COMPENSATED_B_A, after.getStatus());
        assertEquals(DepositStatus.REFUNDED, b.findDeposit(after.getReservationId()).getStatus());
        assertEquals(ReservationStatus.CANCELLED, reservationOf(after).getStatus());
    }

    @Test
    void afterRestart_decisionIsTakenFromDisk_notFromChainStatus() throws Exception {
        // B finished and persisted, but the process died before the chain row was updated
        Reservation r = a.createReservation("CUST-6", "CAR-002", 2);
        RentalChainRecord row = new RentalChainRecord("CHN-DISK01", "CUST-6", "CAR-002", 2, "BASIC");
        row.markPendingB(r.getReservationId());
        chains.save(row);
        b.process(r.getReservationId(), "CUST-6", "COMPACT", 2, r.getTotalPrice(), "BASIC");
        theStoreIsRestarted();

        RentalChainRecord after = facade.resumeOrInspect("CHN-DISK01");

        assertEquals(ChainStatus.COMPENSATED_B_A, after.getStatus(), "B data on disk must be undone too");
        assertEquals(DepositStatus.REFUNDED, b.findDeposit(r.getReservationId()).getStatus());
    }

    @Test
    void afterRestart_contractFoundOnDisk_chainIsRolledForward() throws Exception {
        Reservation r = a.createReservation("CUST-7", "CAR-001", 2);
        RentalChainRecord row = new RentalChainRecord("CHN-DISK02", "CUST-7", "CAR-001", 2, "BASIC");
        row.markPendingB(r.getReservationId());
        b.process(r.getReservationId(), "CUST-7", "ECONOMY", 2, r.getTotalPrice(), "BASIC");
        row.markPendingC(b.findDeposit(r.getReservationId()).getDepositId());
        chains.save(row);
        c.finalizeRental(r.getReservationId(), "CUST-7", "CAR-001");   // C done, chain not updated
        theStoreIsRestarted();

        RentalChainRecord after = facade.resumeOrInspect("CHN-DISK02");

        assertEquals(ChainStatus.COMPLETED, after.getStatus());
        assertNotNull(after.getContractId());
    }

    @Test
    void afterRestart_crashBeforeChainKnowsReservation_reservationIsAdoptedAndUndone() throws Exception {
        a.createReservation("CUST-8", "CAR-003", 1);   // A committed
        chains.save(new RentalChainRecord("CHN-DISK03", "CUST-8", "CAR-003", 1, "BASIC")); // PENDING_A
        theStoreIsRestarted();

        RentalChainRecord after = facade.resumeOrInspect("CHN-DISK03");

        assertEquals(ChainStatus.COMPENSATED_A, after.getStatus());
        assertTrue(a.isAvailable("CAR-003"));
    }

    @Test
    void afterRestart_reservedCarStaysUnavailable_andGpsDeviceIsNotReused() throws Exception {
        RentalChainRecord first = aCustomerRents("CUST-9", "CAR-001", 2, "BASIC");
        String gps = c.findByReservation(first.getReservationId()).getGpsDeviceId();
        theStoreIsRestarted();

        assertFalse(a.isAvailable("CAR-001"));
        assertThrows(ResourceUnavailableException.class,
                () -> aCustomerRents("CUST-10", "CAR-001", 1, "BASIC"));
        RentalChainRecord second = aCustomerRents("CUST-10", "CAR-002", 1, "BASIC");
        assertNotEquals(gps, c.findByReservation(second.getReservationId()).getGpsDeviceId());
    }

    // ---------------- early failures ----------------

    @Test
    void unknownCar_isRejectedAtA_andRecorded() throws Exception {
        assertThrows(ValidationException.class, () -> aCustomerRents("CUST-1", "CAR-999", 2, "BASIC"));

        assertEquals(1, chains.findAll().size());
        assertEquals(ChainStatus.REJECTED_A, chains.findAll().get(0).getStatus());
    }

    @Test
    void carAlreadyTaken_isRejectedAtA() throws Exception {
        aCustomerRents("CUST-1", "CAR-001", 2, "BASIC");

        assertThrows(ResourceUnavailableException.class,
                () -> aCustomerRents("CUST-2", "CAR-001", 2, "BASIC"));
        assertTrue(chains.findAll().stream().anyMatch(ch -> ch.getStatus() == ChainStatus.REJECTED_A));
    }

    @Test
    void invalidInput_failsBeforeAnythingIsWritten() throws Exception {
        assertThrows(ValidationException.class, () -> aCustomerRents("", "CAR-001", 2, "BASIC"));
        assertThrows(ValidationException.class, () -> aCustomerRents("CUST-1", "", 2, "BASIC"));
        assertThrows(ValidationException.class, () -> aCustomerRents("CUST-1", "CAR-001", 2, "GOLD"));
        assertTrue(chains.findAll().isEmpty());
        assertTrue(a.isAvailable("CAR-001"));
    }

    @Test
    void invalidDays_isRejectedAtA() {
        assertThrows(ValidationException.class, () -> aCustomerRents("CUST-1", "CAR-001", 0, "BASIC"));
        assertThrows(ValidationException.class, () -> aCustomerRents("CUST-1", "CAR-001", 91, "BASIC"));
    }

    // ---------------- admin facade ----------------

    @Test
    void admin_manualCompensation_undoesWholeStuckChain() throws Exception {
        RentalChainRecord stuck = theProcessIsKilledAfter(ChainStatus.PENDING_C, "CUST-5", "CAR-002");

        RentalChainRecord after = admin.manualCompensate(stuck.getChainId());

        assertEquals(ChainStatus.COMPENSATED_B_A, after.getStatus());
        assertEquals(ReservationStatus.CANCELLED, admin.inspectReservation(after.getReservationId()).getStatus());
        assertEquals(DepositStatus.REFUNDED, admin.inspectDeposit(after.getReservationId()).getStatus());
    }

    @Test
    void admin_manualCompensation_afterOnlyA_undoesAlone() throws Exception {
        RentalChainRecord stuck = theProcessIsKilledAfter(ChainStatus.PENDING_B, "CUST-5", "CAR-002");

        assertEquals(ChainStatus.COMPENSATED_A, admin.manualCompensate(stuck.getChainId()).getStatus());
    }

    @Test
    void admin_refusesToCompensateCompletedChain_andUnknownChain() throws Exception {
        RentalChainRecord done = aCustomerRents("CUST-1", "CAR-001", 2, "BASIC");

        assertThrows(CarRentalException.class, () -> admin.manualCompensate(done.getChainId()));
        assertThrows(CarRentalException.class, () -> admin.manualCompensate("CHN-NOPE"));
        assertEquals(1, admin.inspectAllChains().size());
        assertNotNull(admin.inspectContract(done.getReservationId()));
    }

    @Test
    void admin_resetAll_clearsEverything() throws Exception {
        aCustomerRents("CUST-1", "CAR-001", 2, "BASIC");

        admin.resetAll();

        assertTrue(chains.findAll().isEmpty());
        assertTrue(a.isAvailable("CAR-001"));
    }

    @Test
    void resume_ofFinishedOrUnknownChain_changesNothing() throws Exception {
        RentalChainRecord done = aCustomerRents("CUST-1", "CAR-001", 2, "BASIC");

        assertEquals(ChainStatus.COMPLETED, facade.resumeOrInspect(done.getChainId()).getStatus());
        assertThrows(CarRentalException.class, () -> facade.resumeOrInspect("CHN-NOPE"));
        assertTrue(facade.findUnfinishedChains().isEmpty());
    }
}
