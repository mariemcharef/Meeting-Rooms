package com.nordia.rooms;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

/** Creates the tables and loads rooms.json the first time the application starts. */
@Component
public class DatabaseInitializer {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public DatabaseInitializer(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @PostConstruct
    void init() throws IOException {
        jdbc.execute("CREATE TABLE IF NOT EXISTS rooms ("
                + "id INTEGER PRIMARY KEY, name TEXT NOT NULL, capacity INTEGER NOT NULL, "
                + "floor INTEGER NOT NULL, equipment TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS bookings ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "room_id INTEGER NOT NULL REFERENCES rooms(id), "
                + "start_time TEXT NOT NULL, end_time TEXT NOT NULL, "
                + "organiser TEXT NOT NULL, subject TEXT NOT NULL, "
                + "status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'cancelled')))");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_bookings_room_start ON bookings(room_id, start_time)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_bookings_org_start ON bookings(organiser, start_time)");

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        try (InputStream in = new ClassPathResource("rooms.json").getInputStream()) {
            for (JsonNode r : mapper.readTree(in)) {
                jdbc.update("INSERT INTO rooms (id, name, capacity, floor, equipment) VALUES (?, ?, ?, ?, ?)",
                        r.get("id").asInt(), r.get("name").asText(), r.get("capacity").asInt(),
                        r.get("floor").asInt(), r.get("equipment").toString());
            }
        }
    }
}
