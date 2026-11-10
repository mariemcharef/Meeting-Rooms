package com.nordia.rooms;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Pure business rules: no database, no HTTP. Easy to unit test. */
public final class Rules {

    public static final LocalTime OPEN = LocalTime.of(8, 0);
    public static final LocalTime CLOSE = LocalTime.of(19, 0);
    public static final Duration MIN_DURATION = Duration.ofMinutes(15);
    public static final Duration MAX_DURATION = Duration.ofHours(4);
    public static final int MAX_BOOKINGS_PER_DAY = 3;

    private Rules() {
    }

    /** Throws ApiException(422) if the time slot breaks a rule. */
    public static void validateSlot(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new ApiException(422, "invalid_range", "end must be after start.");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new ApiException(422, "multi_day", "A booking must start and end on the same day.");
        }
        DayOfWeek day = start.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new ApiException(422, "weekend", "Bookings are only possible Monday to Friday.");
        }
        if (start.toLocalTime().isBefore(OPEN) || end.toLocalTime().isAfter(CLOSE)) {
            throw new ApiException(422, "outside_hours", "Bookings must be between 08:00 and 19:00.");
        }
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(MIN_DURATION) < 0) {
            throw new ApiException(422, "too_short", "A booking lasts at least 15 minutes.");
        }
        if (duration.compareTo(MAX_DURATION) > 0) {
            throw new ApiException(422, "too_long", "A booking lasts at most 4 hours.");
        }
    }

    public static void checkDailyLimit(int activeCountThatDay) {
        if (activeCountThatDay >= MAX_BOOKINGS_PER_DAY) {
            throw new ApiException(409, "daily_limit",
                    "This organiser already has 3 active bookings on that day.");
        }
    }

    public static void checkCancellable(LocalDateTime start, LocalDateTime now) {
        if (!start.isAfter(now)) {
            throw new ApiException(409, "already_started",
                    "Only a booking that has not started yet can be cancelled.");
        }
    }
}
