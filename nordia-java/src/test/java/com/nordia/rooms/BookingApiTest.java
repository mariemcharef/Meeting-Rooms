package com.nordia.rooms;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

// 2026-11-10 is a Tuesday, 2026-11-14 a Saturday.
@SpringBootTest
@AutoConfigureMockMvc
class BookingApiTest {

    private static final Path DB_FILE = tempDb();

    private static Path tempDb() {
        try {
            Path p = Files.createTempFile("nordia-test", ".db");
            p.toFile().deleteOnExit();
            return p;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_FILE.toAbsolutePath());
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM bookings");
        clock.set(LocalDateTime.of(2026, 11, 9, 12, 0));
    }

    // ---- helpers -------------------------------------------------------------------------

    private ResultActions book(String start, String end, int roomId, String organiser) throws Exception {
        String body = """
                {"room_id": %d, "start": "%s", "end": "%s", "organiser": "%s", "subject": "Sync"}
                """.formatted(roomId, start, end, organiser);
        return mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions book(String start, String end) throws Exception {
        return book(start, end, 1, "alice");
    }

    private long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static String at(int hour, int minute) {
        return "2026-11-10T%02d:%02d:00".formatted(hour, minute);
    }

    // ---- tests ---------------------------------------------------------------------------

    @Test
    void listRoomsAndFilterByMinCapacity() throws Exception {
        mvc.perform(get("/rooms")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)));
        mvc.perform(get("/rooms?min_capacity=10")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Delta", "Equinox", "Fjord")));
        mvc.perform(get("/rooms?min_capacity=0")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createBookingOk() throws Exception {
        book(at(9, 0), at(10, 0)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.room_id").value(1))
                .andExpect(jsonPath("$.start").value("2026-11-10T09:00:00"));
    }

    @Test
    void unknownRoomIs404() throws Exception {
        book(at(9, 0), at(10, 0), 99, "alice").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("room_not_found"));
    }

    @Test
    void overlapIsRejectedWith409() throws Exception {
        book(at(9, 0), at(10, 0)).andExpect(status().isCreated());
        book(at(9, 30), at(10, 30), 1, "bob").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("overlap"));
    }

    @Test
    void backToBackAndOtherRoomAreAllowed() throws Exception {
        book(at(9, 0), at(10, 0)).andExpect(status().isCreated());
        book(at(10, 0), at(11, 0), 1, "bob").andExpect(status().isCreated());
        book(at(9, 0), at(10, 0), 2, "carol").andExpect(status().isCreated());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-14T09:00:00, 2026-11-14T10:00:00, weekend",
            "2026-11-10T07:30:00, 2026-11-10T08:30:00, outside_hours",
            "2026-11-10T18:30:00, 2026-11-10T19:30:00, outside_hours",
            "2026-11-10T09:00:00, 2026-11-10T09:10:00, too_short",
            "2026-11-10T09:00:00, 2026-11-10T13:01:00, too_long",
            "2026-11-10T10:00:00, 2026-11-10T09:00:00, invalid_range",
            "2026-11-10T17:00:00, 2026-11-11T09:00:00, multi_day"
    })
    void slotRulesAreRejectedWith422(String start, String end, String code) throws Exception {
        book(start, end).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(code));
    }

    @Test
    void boundariesAreAccepted() throws Exception {
        book(at(8, 0), at(8, 15)).andExpect(status().isCreated());
        book(at(15, 0), at(19, 0), 2, "alice").andExpect(status().isCreated());
        book(at(9, 0), at(13, 0), 3, "alice").andExpect(status().isCreated());
    }

    @Test
    void fourthBookingSameDayIsRejectedButNextDayIsFine() throws Exception {
        book(at(9, 0), at(9, 30)).andExpect(status().isCreated());
        book(at(10, 0), at(10, 30)).andExpect(status().isCreated());
        book(at(11, 0), at(11, 30)).andExpect(status().isCreated());
        book(at(12, 0), at(12, 30)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("daily_limit"));
        book("2026-11-11T09:00:00", "2026-11-11T09:30:00").andExpect(status().isCreated());
    }

    @Test
    void cancelledBookingFreesSlotAndQuota() throws Exception {
        long first = idOf(book(at(9, 0), at(9, 30)));
        book(at(10, 0), at(10, 30));
        book(at(11, 0), at(11, 30));
        mvc.perform(delete("/bookings/" + first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
        book(at(9, 0), at(9, 30)).andExpect(status().isCreated());
    }

    @Test
    void cancelOfAStartedBookingIsRejected() throws Exception {
        long id = idOf(book(at(9, 0), at(10, 0)));
        clock.set(LocalDateTime.of(2026, 11, 10, 9, 30)); // booking is in progress
        mvc.perform(delete("/bookings/" + id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("already_started"));
    }

    @Test
    void cancelUnknownAndCancelTwice() throws Exception {
        mvc.perform(delete("/bookings/999")).andExpect(status().isNotFound());
        long id = idOf(book(at(9, 0), at(10, 0)));
        mvc.perform(delete("/bookings/" + id)).andExpect(status().isOk());
        mvc.perform(delete("/bookings/" + id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("already_cancelled"));
    }

    @Test
    void listRoomBookingsForADay() throws Exception {
        book(at(14, 0), at(15, 0));
        book(at(9, 0), at(10, 0), 1, "bob");
        book("2026-11-11T09:00:00", "2026-11-11T10:00:00");
        long gone = idOf(book(at(11, 0), at(12, 0), 1, "cy"));
        mvc.perform(delete("/bookings/" + gone)).andExpect(status().isOk());

        mvc.perform(get("/rooms/1/bookings?date=2026-11-10")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].start", contains("2026-11-10T09:00:00", "2026-11-10T14:00:00")));
        mvc.perform(get("/rooms/99/bookings?date=2026-11-10")).andExpect(status().isNotFound());
    }

    @Test
    void invalidInputGivesClear422() throws Exception {
        mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"room_id\":1,\"start\":\"nope\",\"end\":\"2026-11-10T10:00:00\","
                                + "\"organiser\":\"alice\",\"subject\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("invalid_input"));
        mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"room_id\":1,\"start\":\"2026-11-10T09:00:00\",\"end\":\"2026-11-10T10:00:00\","
                                + "\"organiser\":\"  \",\"subject\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("invalid_input"));
        mvc.perform(get("/rooms/1/bookings")).andExpect(status().isUnprocessableEntity());
        book("2026-11-10T09:00:00+01:00", "2026-11-10T10:00:00+01:00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("timezone_not_supported"));
    }
}
