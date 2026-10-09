package com.carrental.subsystem;

import com.carrental.exception.*;
import com.carrental.subsystem.a.*;
import com.carrental.subsystem.b.*;
import com.carrental.subsystem.c.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Each subsystem works completely on its own, without any facade. */
class SubsystemStandaloneTest {

    @TempDir
    Path tempDir;

    private String dbUrl;

    @BeforeEach
    void init() {
        dbUrl = "jdbc:sqlite:" + tempDir.resolve("standalone.db").toAbsolutePath();
    }

    @Test
    void subsystemA_worksStandalone() throws Exception {
        ReservationService a = new ReservationService(
                new PricingEngine(), new FleetAvailabilityTracker(), new ReservationRepository(dbUrl));

        Reservation r = a.createReservation("CUST-A", "CAR-001", 3);
        assertEquals(ReservationStatus.RESERVED, r.getStatus());
        assertFalse(a.isAvailable("CAR-001"));

        a.cancelReservation(r.getReservationId());
        assertEquals(ReservationStatus.CANCELLED, a.findById(r.getReservationId()).getStatus());
        assertTrue(a.isAvailable("CAR-001"));
    }

    @Test
    void subsystemB_worksStandalone() throws Exception {
        ProcessingService b = new ProcessingService(
                new InsuranceCalculator(), new DepositHandler(), new DepositRepository(dbUrl));

        Deposit d = b.process("RES-DUMMY", "CUST-B", "ECONOMY", 2, 70.0, "FULL");
        assertEquals(DepositStatus.HELD, d.getStatus());
        assertEquals(InsuranceStatus.ACTIVE, b.findInsurance("RES-DUMMY").getStatus());

        b.compensate("RES-DUMMY");
        b.compensate("RES-DUMMY");   // idempotent
        assertEquals(DepositStatus.REFUNDED, b.findDeposit("RES-DUMMY").getStatus());
        assertEquals(InsuranceStatus.CANCELLED, b.findInsurance("RES-DUMMY").getStatus());
    }

    @Test
    void subsystemC_worksStandalone() throws Exception {
        FinalizationService c = new FinalizationService(
                new DamageInspector(), new GpsTracker(), new ContractRepository(dbUrl));

        RentalContract contract = c.finalizeRental("RES-DUMMY", "CUST-C", "CAR-001");
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertNotNull(contract.getGpsDeviceId());

        c.compensate("RES-DUMMY");
        assertEquals(ContractStatus.CANCELLED, c.findByReservation("RES-DUMMY").getStatus());
    }

    // ---------- pure business logic ----------

    @Test
    void pricingEngine_appliesLongRentalSurchargeAndLoyaltyDiscount() throws Exception {
        PricingEngine p = new PricingEngine();
        Car car = new Car("X", "Test", "ECONOMY", 100.0);

        assertEquals(200.0, p.calculateTotalPrice(car, 2, "CUST-1"));
        assertEquals(330.0, p.calculateTotalPrice(car, 3, "CUST-1"));
        assertEquals(313.5, p.calculateTotalPrice(car, 3, "LOYAL-1"));
        assertThrows(ValidationException.class, () -> p.calculateTotalPrice(car, 0, "CUST-1"));
        assertThrows(ValidationException.class, () -> p.calculateTotalPrice(null, 1, "CUST-1"));
        assertThrows(ValidationException.class, () -> p.calculateTotalPrice(car, 1, " "));
    }

    @Test
    void insuranceCalculator_computesPremiumAndDeposit() throws Exception {
        InsuranceCalculator calc = new InsuranceCalculator();

        assertEquals(16.0, calc.calculatePremium("ECONOMY", 2, "BASIC"));
        assertEquals(25.6, calc.calculatePremium("ECONOMY", 2, "FULL"));
        assertEquals(20.0, calc.calculateDepositAmount("ECONOMY", 100.0));
        assertEquals(40.0, calc.calculateDepositAmount("LUXURY", 100.0));
        assertThrows(ValidationException.class, () -> calc.calculatePremium("ECONOMY", 2, "GOLD"));
        assertThrows(ValidationException.class, () -> calc.validateCoverage(null));
    }

    @Test
    void depositHandler_declinesAmountAboveLimit() {
        DepositHandler h = new DepositHandler();

        assertThrows(ResourceUnavailableException.class,
                () -> h.validateAndHold(DepositHandler.MAX_DEPOSIT + 1, "CUST-1"));
        assertThrows(ValidationException.class, () -> h.validateAndHold(0, "CUST-1"));
    }

    // ---------- status machines enforce legal transitions on their own ----------

    @Test
    void reservation_rejectsIllegalTransitions() throws Exception {
        Reservation r = new Reservation("R", "C", "CAR", 1, 10.0);
        r.reserve();
        assertThrows(InvalidTransitionException.class, r::reserve);
        r.cancel();
        assertThrows(InvalidTransitionException.class, r::cancel);
    }

    @Test
    void deposit_rejectsIllegalTransitions() throws Exception {
        Deposit d = new Deposit("D", "R", "C", 10.0);
        assertThrows(InvalidTransitionException.class, d::refund);   // PENDING -> REFUNDED illegal
        d.hold();
        d.forfeit();
        assertThrows(InvalidTransitionException.class, d::refund);
    }

    @Test
    void insuranceAndContract_rejectIllegalTransitions() throws Exception {
        InsurancePolicy p = new InsurancePolicy("P", "R", "C", 5.0, "BASIC");
        p.activate();
        assertThrows(InvalidTransitionException.class, p::activate);
        p.cancel();
        assertThrows(InvalidTransitionException.class, p::cancel);

        RentalContract k = new RentalContract("K", "R", "C", "CAR", true, "GPS-001");
        assertThrows(InvalidTransitionException.class, k::complete);   // DRAFT -> COMPLETED illegal
        k.activate();
        k.complete();
        assertThrows(InvalidTransitionException.class, k::cancel);
    }

    @Test
    void gpsTracker_runsOutOfDevices() throws Exception {
        GpsTracker g = new GpsTracker(1);
        String d = g.assignDevice("CAR-1");
        assertThrows(ResourceUnavailableException.class, () -> g.assignDevice("CAR-2"));
        g.releaseDevice(d);
        assertEquals(1, g.freeCount());
    }
}
