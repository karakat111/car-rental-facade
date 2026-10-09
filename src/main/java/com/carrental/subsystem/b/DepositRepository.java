package com.carrental.subsystem.b;

import com.carrental.exception.CarRentalException;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DepositRepository {

    private final String jdbcUrl;

    public DepositRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initSchema();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initSchema() {
        String depositSql = """
            CREATE TABLE IF NOT EXISTS deposits (
                deposit_id     TEXT PRIMARY KEY,
                reservation_id TEXT NOT NULL,
                customer_id    TEXT NOT NULL,
                amount         REAL NOT NULL,
                status         TEXT NOT NULL,
                created_at     TEXT NOT NULL,
                updated_at     TEXT NOT NULL
            )
            """;
        String insuranceSql = """
            CREATE TABLE IF NOT EXISTS insurance_policies (
                policy_id      TEXT PRIMARY KEY,
                reservation_id TEXT NOT NULL,
                customer_id    TEXT NOT NULL,
                premium        REAL NOT NULL,
                coverage_type  TEXT NOT NULL,
                status         TEXT NOT NULL,
                created_at     TEXT NOT NULL,
                updated_at     TEXT NOT NULL
            )
            """;
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute(depositSql);
            st.execute(insuranceSql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init B schema", e);
        }
    }

    public void saveDeposit(Deposit d) throws CarRentalException {
        String sql = """
            INSERT OR REPLACE INTO deposits
            (deposit_id, reservation_id, customer_id, amount, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, d.getDepositId());
            ps.setString(2, d.getReservationId());
            ps.setString(3, d.getCustomerId());
            ps.setDouble(4, d.getAmount());
            ps.setString(5, d.getStatus().name());
            ps.setString(6, d.getCreatedAt().toString());
            ps.setString(7, d.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new CarRentalException("Failed to save deposit", e);
        }
    }

    public void saveInsurance(InsurancePolicy p) throws CarRentalException {
        String sql = """
            INSERT OR REPLACE INTO insurance_policies
            (policy_id, reservation_id, customer_id, premium, coverage_type, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.getPolicyId());
            ps.setString(2, p.getReservationId());
            ps.setString(3, p.getCustomerId());
            ps.setDouble(4, p.getPremium());
            ps.setString(5, p.getCoverageType());
            ps.setString(6, p.getStatus().name());
            ps.setString(7, p.getCreatedAt().toString());
            ps.setString(8, p.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new CarRentalException("Failed to save insurance", e);
        }
    }

    public Optional<Deposit> findDepositByReservation(String reservationId) throws CarRentalException {
        String sql = "SELECT * FROM deposits WHERE reservation_id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapDeposit(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new CarRentalException("Failed to load deposit", e);
        }
    }

    public Optional<InsurancePolicy> findInsuranceByReservation(String reservationId)
            throws CarRentalException {
        String sql = "SELECT * FROM insurance_policies WHERE reservation_id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapInsurance(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new CarRentalException("Failed to load insurance", e);
        }
    }

    public List<Deposit> findAllDeposits() throws CarRentalException {
        List<Deposit> list = new ArrayList<>();
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM deposits")) {
            while (rs.next()) list.add(mapDeposit(rs));
        } catch (SQLException e) {
            throw new CarRentalException("Failed to list deposits", e);
        }
        return list;
    }

    public void deleteAll() throws CarRentalException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM deposits");
            st.execute("DELETE FROM insurance_policies");
        } catch (SQLException e) {
            throw new CarRentalException("Failed to clear B tables", e);
        }
    }

    private Deposit mapDeposit(ResultSet rs) throws SQLException {
        return new Deposit(
                rs.getString("deposit_id"),
                rs.getString("reservation_id"),
                rs.getString("customer_id"),
                rs.getDouble("amount"),
                DepositStatus.valueOf(rs.getString("status")),
                LocalDateTime.parse(rs.getString("created_at")),
                LocalDateTime.parse(rs.getString("updated_at"))
        );
    }

    private InsurancePolicy mapInsurance(ResultSet rs) throws SQLException {
        return new InsurancePolicy(
                rs.getString("policy_id"),
                rs.getString("reservation_id"),
                rs.getString("customer_id"),
                rs.getDouble("premium"),
                rs.getString("coverage_type"),
                InsuranceStatus.valueOf(rs.getString("status")),
                LocalDateTime.parse(rs.getString("created_at")),
                LocalDateTime.parse(rs.getString("updated_at"))
        );
    }
}
