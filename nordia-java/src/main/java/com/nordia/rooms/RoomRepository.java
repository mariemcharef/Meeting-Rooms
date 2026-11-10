package com.nordia.rooms;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class RoomRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RoomRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<Room> findAll(Integer minCapacity) {
        int min = minCapacity == null ? 0 : minCapacity;
        return jdbc.query("SELECT * FROM rooms WHERE capacity >= ? ORDER BY id",
                (rs, i) -> new Room(rs.getInt("id"), rs.getString("name"), rs.getInt("capacity"),
                        rs.getInt("floor"), equipment(rs.getString("equipment"))),
                min);
    }

    public boolean exists(int id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM rooms WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    private List<String> equipment(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt equipment data: " + json, e);
        }
    }
}
