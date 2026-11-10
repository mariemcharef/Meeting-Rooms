package com.nordia.rooms;

/** start and end are ISO local date-times as text, e.g. 2026-11-10T09:00:00. */
public record Booking(long id, int roomId, String start, String end,
                      String organiser, String subject, String status) {
}
