package com.carrental.subsystem.c;

import com.carrental.exception.CarRentalException;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ContractRepository {

    private final String jdbcUrl;

    public ContractRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initSchema();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initSchema() {
        String sql = """
            CREATE TABLE IF NOT EXISTS rental_contracts (
                contract_id    TEXT PRIMARY KEY,
                reservation_id TEXT NOT NULL,
                customer_id    TEXT NOT NULL,
                car_id         TEXT NOT NULL,
                damage_ok      INTEGER NOT NULL,
                gps_device_id  TEXT,
                status         TEXT NOT NULL,
                created_at     TEXT NOT NULL,
                updated_at     TEXT NOT NULL
            )
            """;
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init C schema", e);
        }
    }

    public void save(RentalContract contract) throws CarRentalException {
        String sql = """
            INSERT OR REPLACE INTO rental_contracts
            (contract_id, reservation_id, customer_id, car_id, damage_ok, gps_device_id, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, contract.getContractId());
            ps.setString(2, contract.getReservationId());
            ps.setString(3, contract.getCustomerId());
            ps.setString(4, contract.getCarId());
            ps.setInt(5, contract.isDamageOk() ? 1 : 0);
            ps.setString(6, contract.getGpsDeviceId());
            ps.setString(7, contract.getStatus().name());
            ps.setString(8, contract.getCreatedAt().toString());
            ps.setString(9, contract.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new CarRentalException("Failed to save contract", e);
        }
    }

    public Optional<RentalContract> findByReservation(String reservationId) throws CarRentalException {
        String sql = "SELECT * FROM rental_contracts WHERE reservation_id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new CarRentalException("Failed to load contract", e);
        }
    }

    /** GPS devices used by contracts that are still DRAFT or ACTIVE. */
    public List<String> findActiveGpsDevices() throws CarRentalException {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT gps_device_id FROM rental_contracts "
                + "WHERE status IN ('DRAFT','ACTIVE') AND gps_device_id IS NOT NULL";
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getString("gps_device_id"));
            }
        } catch (SQLException e) {
            throw new CarRentalException("Failed to load GPS assignments", e);
        }
        return ids;
    }

    public List<RentalContract> findAll() throws CarRentalException {
        List<RentalContract> list = new ArrayList<>();
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM rental_contracts")) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            throw new CarRentalException("Failed to list contracts", e);
        }
        return list;
    }

    public void deleteAll() throws CarRentalException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM rental_contracts");
        } catch (SQLException e) {
            throw new CarRentalException("Failed to clear contracts", e);
        }
    }

    private RentalContract map(ResultSet rs) throws SQLException {
        return new RentalContract(
                rs.getString("contract_id"),
                rs.getString("reservation_id"),
                rs.getString("customer_id"),
                rs.getString("car_id"),
                rs.getInt("damage_ok") == 1,
                rs.getString("gps_device_id"),
                ContractStatus.valueOf(rs.getString("status")),
                LocalDateTime.parse(rs.getString("created_at")),
                LocalDateTime.parse(rs.getString("updated_at"))
        );
    }
}
