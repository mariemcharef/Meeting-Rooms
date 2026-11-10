package com.nordia.rooms;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {

    private static final RowMapper<Booking> MAPPER = (rs, i) -> new Booking(
            rs.getLong("id"), rs.getInt("room_id"), rs.getString("start_time"), rs.getString("end_time"),
            rs.getString("organiser"), rs.getString("subject"), rs.getString("status"));

    private final JdbcTemplate jdbc;

    public BookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Two bookings overlap if each one starts before the other ends. Cancelled ones are ignored. */
    public boolean hasOverlap(int roomId, LocalDateTime start, LocalDateTime end) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE room_id = ? AND status = 'active' "
                        + "AND start_time < ? AND end_time > ?",
                Integer.class, roomId, Times.format(end), Times.format(start));
        return n != null && n > 0;
    }

    /** Active bookings of an organiser (case-insensitive) whose start date is the given day. */
    public int countActiveForOrganiserOn(String organiser, LocalDate day) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE lower(organiser) = lower(?) AND status = 'active' "
                        + "AND start_time >= ? AND start_time < ?",
                Integer.class, organiser,
                Times.format(day.atStartOfDay()), Times.format(day.plusDays(1).atStartOfDay()));
        return n == null ? 0 : n;
    }

    /** Must be called inside the transaction opened by BookingService (same connection). */
    public Booking insert(int roomId, LocalDateTime start, LocalDateTime end, String organiser, String subject) {
        jdbc.update("INSERT INTO bookings (room_id, start_time, end_time, organiser, subject) "
                + "VALUES (?, ?, ?, ?, ?)",
                roomId, Times.format(start), Times.format(end), organiser, subject);
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        return findById(id).orElseThrow();
    }

    public Optional<Booking> findById(long id) {
        return jdbc.query("SELECT * FROM bookings WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public void markCancelled(long id) {
        jdbc.update("UPDATE bookings SET status = 'cancelled' WHERE id = ?", id);
    }

    public List<Booking> findActiveByRoomAndDay(int roomId, LocalDate day) {
        return jdbc.query(
                "SELECT * FROM bookings WHERE room_id = ? AND status = 'active' "
                        + "AND start_time >= ? AND start_time < ? ORDER BY start_time",
                MAPPER, roomId, Times.format(day.atStartOfDay()), Times.format(day.plusDays(1).atStartOfDay()));
    }
}
