package com.carrental.subsystem.a;

import com.carrental.exception.CarRentalException;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistence gateway of Subsystem A. Talks directly to SQLite.
 * No knowledge of Subsystems B or C.
 */
public class ReservationRepository {

    private final String jdbcUrl;

    public ReservationRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initSchema();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initSchema() {
        String sql = """
            CREATE TABLE IF NOT EXISTS reservations (
                reservation_id TEXT PRIMARY KEY,
                customer_id    TEXT NOT NULL,
                car_id         TEXT NOT NULL,
                rental_days    INTEGER NOT NULL,
                total_price    REAL NOT NULL,
                status         TEXT NOT NULL,
                created_at     TEXT NOT NULL,
                updated_at     TEXT NOT NULL
            )
            """;
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init reservations schema", e);
        }
    }

    public void save(Reservation r) throws CarRentalException {
        String sql = """
            INSERT OR REPLACE INTO reservations
            (reservation_id, customer_id, car_id, rental_days, total_price, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.getReservationId());
            ps.setString(2, r.getCustomerId());
            ps.setString(3, r.getCarId());
            ps.setInt(4, r.getRentalDays());
            ps.setDouble(5, r.getTotalPrice());
            ps.setString(6, r.getStatus().name());
            ps.setString(7, r.getCreatedAt().toString());
            ps.setString(8, r.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new CarRentalException("Failed to save reservation " + r.getReservationId(), e);
        }
    }

    public Optional<Reservation> findById(String id) throws CarRentalException {
        String sql = "SELECT * FROM reservations WHERE reservation_id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new CarRentalException("Failed to load reservation " + id, e);
        }
    }

    public List<Reservation> findAll() throws CarRentalException {
        List<Reservation> list = new ArrayList<>();
        String sql = "SELECT * FROM reservations ORDER BY created_at";
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new CarRentalException("Failed to list reservations", e);
        }
        return list;
    }

    /** Ids of all cars that are currently held by a RESERVED reservation. */
    public List<String> findReservedCarIds() throws CarRentalException {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT DISTINCT car_id FROM reservations WHERE status = 'RESERVED'";
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getString("car_id"));
            }
        } catch (SQLException e) {
            throw new CarRentalException("Failed to load reserved cars", e);
        }
        return ids;
    }

    /** The RESERVED reservation of this customer for this car, if any. */
    public Optional<Reservation> findActive(String customerId, String carId) throws CarRentalException {
        String sql = "SELECT * FROM reservations WHERE customer_id = ? AND car_id = ? AND status = 'RESERVED'";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, customerId);
            ps.setString(2, carId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new CarRentalException("Failed to load active reservation", e);
        }
    }

    public void deleteAll() throws CarRentalException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM reservations");
        } catch (SQLException e) {
            throw new CarRentalException("Failed to clear reservations", e);
        }
    }

    private Reservation map(ResultSet rs) throws SQLException {
        return new Reservation(
                rs.getString("reservation_id"),
                rs.getString("customer_id"),
                rs.getString("car_id"),
                rs.getInt("rental_days"),
                rs.getDouble("total_price"),
                ReservationStatus.valueOf(rs.getString("status")),
                LocalDateTime.parse(rs.getString("created_at")),
                LocalDateTime.parse(rs.getString("updated_at"))
        );
    }
}
