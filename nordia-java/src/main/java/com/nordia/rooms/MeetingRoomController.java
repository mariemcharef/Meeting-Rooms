package com.nordia.rooms;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class MeetingRoomController {

    private final RoomRepository rooms;
    private final BookingService service;

    public MeetingRoomController(RoomRepository rooms, BookingService service) {
        this.rooms = rooms;
        this.service = service;
    }

    @GetMapping("/rooms")
    public List<Room> listRooms(@RequestParam(name = "min_capacity", required = false) Integer minCapacity) {
        if (minCapacity != null && minCapacity < 1) {
            throw new ApiException(422, "invalid_input", "min_capacity: must be at least 1.");
        }
        return rooms.findAll(minCapacity);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public Booking createBooking(@Valid @RequestBody BookingRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/bookings/{id}")
    public Booking cancelBooking(@PathVariable long id) {
        return service.cancel(id);
    }

    @GetMapping("/rooms/{id}/bookings")
    public List<Booking> roomBookings(@PathVariable int id,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.listForRoomAndDay(id, date);
    }
}
