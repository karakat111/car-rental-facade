package com.carrental.demo;

import com.carrental.exception.CarRentalException;
import com.carrental.exception.SimulatedCrashException;
import com.carrental.facade.*;
import com.carrental.subsystem.a.*;
import com.carrental.subsystem.b.*;
import com.carrental.subsystem.c.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * End-to-end CLI demo. The examiner can run the full A->B->C chain, trigger both
 * compensation depths, kill the process mid-chain, restart and recover, inspect
 * the persisted state at any time and reset the store.
 */
public class CarRentalDemoApp {

    private static final String DB_URL =
            "jdbc:sqlite:" + System.getProperty("carrental.db", "car_rental.db");

    private final CarRentalFacade facade;
    private final AdminFacade admin;
    private final Scanner in = new Scanner(System.in);

    public CarRentalDemoApp() {
        ReservationService a = new ReservationService(
                new PricingEngine(), new FleetAvailabilityTracker(), new ReservationRepository(DB_URL));
        ProcessingService b = new ProcessingService(
                new InsuranceCalculator(), new DepositHandler(), new DepositRepository(DB_URL));
        FinalizationService c = new FinalizationService(
                new DamageInspector(), new GpsTracker(), new ContractRepository(DB_URL));
        ChainRepository chains = new ChainRepository(DB_URL);

        this.facade = new CarRentalFacade(a, b, c, chains);
        this.admin = new AdminFacade(a, b, c, chains);
    }

    public static void main(String[] args) {
        System.out.println("=== Car Rental Facade Demo ===");
        System.out.println("Store: " + DB_URL + " (SQLite, survives restart)\n");
        new CarRentalDemoApp().run();
    }

    private void run() {
        warnAboutUnfinishedChains();
        try {
            while (true) {
                printMenu();
                String choice = in.nextLine().trim();
                try {
                    switch (choice) {
                        case "1" -> listFleet();
                        case "2" -> rent();
                        case "3" -> rentWithBFailure();
                        case "4" -> rentWithCFailure();
                        case "5" -> inspectChains();
                        case "6" -> inspectDetails();
                        case "7" -> manualCompensate();
                        case "8" -> recover();
                        case "9" -> reset();
                        case "10" -> crashAfter(ChainStatus.PENDING_B);
                        case "11" -> crashAfter(ChainStatus.PENDING_C);
                        case "0" -> { System.out.println("Bye."); return; }
                        default -> System.out.println("Unknown option");
                    }
                } catch (CarRentalException | NumberFormatException e) {
                    System.out.println("ERROR: " + e.getMessage());
                }
                System.out.println();
            }
        } catch (NoSuchElementException endOfInput) {
            System.out.println("Bye.");
        }
    }

    private void printMenu() {
        System.out.print("""
            ----------------------------------------
             1. List fleet (with availability)
             2. Rent a car (A -> B -> C)
             3. Rent with B failure  (compensate A only)
             4. Rent with C failure  (compensate B, then A)
             5. Inspect all chains (persisted)
             6. Inspect reservation / deposit / contract
             7. Manual compensation (AdminFacade)
             8. Recover unfinished chains after restart
             9. Reset entire store
            10. Rent and KILL the process after A  (leaves PENDING_B)
            11. Rent and KILL the process after B  (leaves PENDING_C)
             0. Exit
            ----------------------------------------
            Choice: """);
    }

    private void warnAboutUnfinishedChains() {
        try {
            List<RentalChainRecord> stuck = facade.findUnfinishedChains();
            if (!stuck.isEmpty()) {
                System.out.println("!! Found " + stuck.size()
                        + " unfinished chain(s) on disk (previous run did not finish):");
                stuck.forEach(c -> System.out.println("   " + c));
                System.out.println("!! Use option 8 to recover them.\n");
            }
        } catch (CarRentalException e) {
            System.out.println("Could not read chains: " + e.getMessage());
        }
    }

    private void listFleet() {
        System.out.println("Fleet:");
        facade.listFleet().values().stream()
                .sorted(java.util.Comparator.comparing(Car::getCarId))
                .forEach(c -> System.out.printf("  %s | %-15s | %-7s | $%6.2f/day | %s%n",
                        c.getCarId(), c.getModel(), c.getCategory(), c.getDailyRate(),
                        facade.isCarAvailable(c.getCarId()) ? "AVAILABLE" : "RESERVED"));
    }

    private String ask(String prompt) {
        System.out.print(prompt);
        return in.nextLine().trim();
    }

    private RentalChainRecord rentFromInput() throws CarRentalException {
        String customer = ask("Customer id (e.g. CUST-001 or LOYAL-001): ");
        String carId = ask("Car id (e.g. CAR-001): ");
        int days = Integer.parseInt(ask("Rental days (1-90): "));
        String coverage = ask("Coverage (BASIC/FULL/PREMIUM): ");
        RentalChainRecord result = facade.rentCar(customer, carId, days, coverage);
        System.out.println("Result: " + result);
        return result;
    }

    private void rent() throws CarRentalException {
        rentFromInput();
    }

    private void rentWithBFailure() throws CarRentalException {
        System.out.println(">>> Payment gateway will be down for the next deposit");
        System.out.println("    (tip: a 30-day LUXURY rental also fails for real - limit "
                + DepositHandler.MAX_DEPOSIT + ")");
        admin.forceBFailureNext(true);
        try {
            rentFromInput();
        } finally {
            admin.forceBFailureNext(false);
        }
    }

    private void rentWithCFailure() throws CarRentalException {
        System.out.println(">>> Damage station will be offline for the next inspection");
        System.out.println("    (tip: GPS pool has " + GpsTracker.DEFAULT_POOL_SIZE
                + " devices - the next rental after that fails in C for real)");
        admin.forceCFailureNext(true);
        try {
            rentFromInput();
        } finally {
            admin.forceCFailureNext(false);
        }
    }

    private void crashAfter(ChainStatus status) throws CarRentalException {
        System.out.println(">>> The JVM will be killed right after " + status + " is written to disk.");
        facade.simulateCrashAfter(status);
        try {
            rentFromInput();
        } catch (SimulatedCrashException crash) {
            System.out.println("*** " + crash.getMessage() + " ***");
            System.out.println("*** Start the application again and use option 8 / option 5 ***");
            System.out.flush();
            Runtime.getRuntime().halt(137);   // no shutdown hooks, like kill -9
        } finally {
            facade.simulateCrashAfter(null);
        }
    }

    private void inspectChains() throws CarRentalException {
        List<RentalChainRecord> chains = facade.listAllChains();
        if (chains.isEmpty()) {
            System.out.println("(no chains yet)");
        }
        chains.forEach(c -> System.out.println("  " + c));
    }

    private void inspectDetails() {
        String resId = ask("Reservation id: ");
        try { System.out.println("Reservation: " + admin.inspectReservation(resId)); }
        catch (CarRentalException e) { System.out.println("Reservation: " + e.getMessage()); }
        try { System.out.println("Deposit    : " + admin.inspectDeposit(resId)); }
        catch (CarRentalException e) { System.out.println("Deposit    : " + e.getMessage()); }
        try { System.out.println("Contract   : " + admin.inspectContract(resId)); }
        catch (CarRentalException e) { System.out.println("Contract   : " + e.getMessage()); }
    }

    private void manualCompensate() throws CarRentalException {
        String chainId = ask("Chain id: ");
        System.out.println("After manual compensation: " + admin.manualCompensate(chainId));
    }

    private void recover() throws CarRentalException {
        List<RentalChainRecord> stuck = facade.findUnfinishedChains();
        if (stuck.isEmpty()) {
            System.out.println("Nothing to recover - no unfinished chains on disk.");
            return;
        }
        for (RentalChainRecord c : stuck) {
            System.out.println("Before: " + c);
            System.out.println("After : " + facade.resumeOrInspect(c.getChainId()));
        }
    }

    private void reset() throws CarRentalException {
        admin.resetAll();
        System.out.println("All tables cleared. Fleet availability restored.");
    }
}
