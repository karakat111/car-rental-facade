package com.carrental.facade;

import com.carrental.exception.CarRentalException;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChainRepository {

    private final String jdbcUrl;

    public ChainRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initSchema();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initSchema() {
        String sql = """
            CREATE TABLE IF NOT EXISTS rental_chains (
                chain_id       TEXT PRIMARY KEY,
                customer_id    TEXT NOT NULL,
                car_id         TEXT NOT NULL,
                rental_days    INTEGER NOT NULL,
                coverage_type  TEXT NOT NULL,
                reservation_id TEXT,
                deposit_id     TEXT,
                contract_id    TEXT,
                status         TEXT NOT NULL,
                created_at     TEXT NOT NULL,
                updated_at     TEXT NOT NULL,
                last_error     TEXT
            )
            """;
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init chain schema", e);
        }
    }

    public void save(RentalChainRecord r) throws CarRentalException {
        String sql = """
            INSERT OR REPLACE INTO rental_chains
            (chain_id, customer_id, car_id, rental_days, coverage_type,
             reservation_id, deposit_id, contract_id, status, created_at, updated_at, last_error)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.getChainId());
            ps.setString(2, r.getCustomerId());
            ps.setString(3, r.getCarId());
            ps.setInt(4, r.getRentalDays());
            ps.setString(5, r.getCoverageType());
            ps.setString(6, r.getReservationId());
            ps.setString(7, r.getDepositId());
            ps.setString(8, r.getContractId());
            ps.setString(9, r.getStatus().name());
            ps.setString(10, r.getCreatedAt().toString());
            ps.setString(11, r.getUpdatedAt().toString());
            ps.setString(12, r.getLastError());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new CarRentalException("Failed to save chain", e);
        }
    }

    public Optional<RentalChainRecord> findById(String id) throws CarRentalException {
        String sql = "SELECT * FROM rental_chains WHERE chain_id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new CarRentalException("Failed to load chain", e);
        }
    }

    public List<RentalChainRecord> findAll() throws CarRentalException {
        List<RentalChainRecord> list = new ArrayList<>();
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM rental_chains ORDER BY created_at")) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            throw new CarRentalException("Failed to list chains", e);
        }
        return list;
    }

    public void deleteAll() throws CarRentalException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM rental_chains");
        } catch (SQLException e) {
            throw new CarRentalException("Failed to clear chains", e);
        }
    }

    private RentalChainRecord map(ResultSet rs) throws SQLException {
        return new RentalChainRecord(
                rs.getString("chain_id"),
                rs.getString("customer_id"),
                rs.getString("car_id"),
                rs.getInt("rental_days"),
                rs.getString("coverage_type"),
                rs.getString("reservation_id"),
                rs.getString("deposit_id"),
                rs.getString("contract_id"),
                ChainStatus.valueOf(rs.getString("status")),
                LocalDateTime.parse(rs.getString("created_at")),
                LocalDateTime.parse(rs.getString("updated_at")),
                rs.getString("last_error")
        );
    }
}
