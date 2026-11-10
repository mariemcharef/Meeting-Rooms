package com.nordia.rooms;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Conversion between text and LocalDateTime. */
public final class Times {

    /**
     * Fixed-width ISO format, always with seconds. LocalDateTime.toString() would drop
     * ":00" seconds ("2026-11-10T09:00"), which would break text comparison in SQL.
     */
    private static final DateTimeFormatter DB = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Pattern OFFSET = Pattern.compile(".*(Z|[+-]\\d{2}:?\\d{2})$");

    private Times() {
    }

    public static String format(LocalDateTime t) {
        return t.format(DB);
    }

    public static LocalDateTime parse(String field, String value) {
        String v = value.trim();
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException e) {
            if (OFFSET.matcher(v).matches()) {
                throw new ApiException(422, "timezone_not_supported",
                        field + ": send local times without a UTC offset (e.g. 2026-11-10T09:00:00).");
            }
            throw new ApiException(422, "invalid_input",
                    field + ": invalid date-time, expected e.g. 2026-11-10T09:00:00.");
        }
    }
}
