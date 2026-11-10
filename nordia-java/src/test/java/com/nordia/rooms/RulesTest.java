package com.nordia.rooms;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** Plain unit tests of the pure rules: no Spring, no database. */
class RulesTest {

    private static LocalDateTime t(String s) {
        return LocalDateTime.parse(s);
    }

    private static String codeOf(String start, String end) {
        return assertThrows(ApiException.class, () -> Rules.validateSlot(t(start), t(end))).code();
    }

    @Test
    void validSlotsAndBoundariesPass() {
        assertDoesNotThrow(() -> Rules.validateSlot(t("2026-11-10T08:00:00"), t("2026-11-10T08:15:00")));
        assertDoesNotThrow(() -> Rules.validateSlot(t("2026-11-10T15:00:00"), t("2026-11-10T19:00:00")));
        assertDoesNotThrow(() -> Rules.validateSlot(t("2026-11-10T09:00:00"), t("2026-11-10T13:00:00")));
    }

    @Test
    void brokenSlotsAreRejectedWithTheRightCode() {
        assertEquals("weekend", codeOf("2026-11-14T09:00:00", "2026-11-14T10:00:00"));
        assertEquals("outside_hours", codeOf("2026-11-10T07:59:00", "2026-11-10T09:00:00"));
        assertEquals("outside_hours", codeOf("2026-11-10T18:30:00", "2026-11-10T19:01:00"));
        assertEquals("too_short", codeOf("2026-11-10T09:00:00", "2026-11-10T09:14:00"));
        assertEquals("too_long", codeOf("2026-11-10T09:00:00", "2026-11-10T13:01:00"));
        assertEquals("invalid_range", codeOf("2026-11-10T10:00:00", "2026-11-10T09:00:00"));
        assertEquals("multi_day", codeOf("2026-11-10T17:00:00", "2026-11-11T09:00:00"));
    }

    @Test
    void dailyLimitAndCancelRules() {
        assertDoesNotThrow(() -> Rules.checkDailyLimit(2));
        assertEquals("daily_limit", assertThrows(ApiException.class, () -> Rules.checkDailyLimit(3)).code());
        assertDoesNotThrow(() -> Rules.checkCancellable(t("2026-11-10T09:00:00"), t("2026-11-10T08:59:59")));
        assertEquals("already_started", assertThrows(ApiException.class,
                () -> Rules.checkCancellable(t("2026-11-10T09:00:00"), t("2026-11-10T09:00:00"))).code());
    }
}
