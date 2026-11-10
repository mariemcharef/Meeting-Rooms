package com.nordia.rooms;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final RoomRepository rooms;
    private final Clock clock;
    private final TransactionTemplate tx;

    // Lock + transaction: the checks and the insert form one atomic step, so two concurrent
    // requests cannot both book the same slot. The lock is taken OUTSIDE the transaction so
    // that the commit happens before the next request enters (an annotation-based
    // @Transactional on a synchronized method would commit after the lock is released).
    private final ReentrantLock writeLock = new ReentrantLock();

    public BookingService(BookingRepository bookings, RoomRepository rooms, Clock clock,
                          PlatformTransactionManager txManager) {
        this.bookings = bookings;
        this.rooms = rooms;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    public Booking create(BookingRequest request) {
        LocalDateTime start = Times.parse("start", request.start()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime end = Times.parse("end", request.end()).truncatedTo(ChronoUnit.SECONDS);
        String organiser = request.organiser().trim();
        String subject = request.subject().trim();
        int roomId = request.roomId();

        Rules.validateSlot(start, end);

        writeLock.lock();
        try {
            return tx.execute(status -> {
                if (!rooms.exists(roomId)) {
                    throw new ApiException(404, "room_not_found", "Room " + roomId + " does not exist.");
                }
                if (bookings.hasOverlap(roomId, start, end)) {
                    throw new ApiException(409, "overlap", "The room is already booked during this time.");
                }
                Rules.checkDailyLimit(bookings.countActiveForOrganiserOn(organiser, start.toLocalDate()));
                return bookings.insert(roomId, start, end, organiser, subject);
            });
        } finally {
            writeLock.unlock();
        }
    }

    public Booking cancel(long bookingId) {
        writeLock.lock();
        try {
            return tx.execute(status -> {
                Booking booking = bookings.findById(bookingId).orElseThrow(() ->
                        new ApiException(404, "booking_not_found", "Booking " + bookingId + " does not exist."));
                if ("cancelled".equals(booking.status())) {
                    throw new ApiException(409, "already_cancelled", "This booking is already cancelled.");
                }
                Rules.checkCancellable(LocalDateTime.parse(booking.start()), LocalDateTime.now(clock));
                bookings.markCancelled(bookingId);
                return bookings.findById(bookingId).orElseThrow();
            });
        } finally {
            writeLock.unlock();
        }
    }

    public List<Booking> listForRoomAndDay(int roomId, LocalDate day) {
        if (!rooms.exists(roomId)) {
            throw new ApiException(404, "room_not_found", "Room " + roomId + " does not exist.");
        }
        return bookings.findActiveByRoomAndDay(roomId, day);
    }
}
