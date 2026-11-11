package com.jankinwu.flynarwhal.web.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quartz-dialect cron parsing/next-execution used by the scheduled thumbnail
 * scan. The expressions mirror what the client's generator produces.
 */
class CronSupportTest {

    private static final ZonedDateTime BASE =
            LocalDateTime.of(2026, 11, 11, 0, 0).atZone(ZoneId.systemDefault());

    @Test
    void parsesSixAndSevenFieldExpressions() {
        assertTrue(CronSupport.isValid("0 0 3 * * ?"));
        assertTrue(CronSupport.isValid("0 0 0 1 1 ? 2027"));
        assertTrue(CronSupport.isValid("0 0/30 9-17 * * ?"));
    }

    @Test
    void parsesQuartzSpecialCharacters() {
        assertTrue(CronSupport.isValid("0 0 0 L * ?"), "last day of month");
        assertTrue(CronSupport.isValid("0 0 0 15W * ?"), "nearest weekday");
        assertTrue(CronSupport.isValid("0 0 0 ? * 2#1"), "first Monday (Quartz 2=Monday)");
        assertTrue(CronSupport.isValid("0 0 0 ? * 6L"), "last Friday of month");
    }

    @Test
    void rejectsInvalidExpressions() {
        assertFalse(CronSupport.isValid(null));
        assertFalse(CronSupport.isValid("  "));
        assertFalse(CronSupport.isValid("not a cron"));
        assertFalse(CronSupport.isValid("0 0"));
        assertFalse(CronSupport.isValid("0 61 * * * ?"), "minute out of range");
        assertThrows(IllegalArgumentException.class, () -> CronSupport.parseOrThrow("0 0 0 * *"));
    }

    @Test
    void computesNextExecution() {
        ZonedDateTime next = CronSupport.nextExecution("0 0 3 * * ?", BASE).orElseThrow();
        assertEquals(LocalDateTime.of(2026, 11, 11, 3, 0), next.toLocalDateTime());

        ZonedDateTime afterFire = CronSupport.nextExecution("0 0 3 * * ?",
                LocalDateTime.of(2026, 11, 11, 3, 0, 1).atZone(ZoneId.systemDefault())).orElseThrow();
        assertEquals(LocalDateTime.of(2026, 11, 12, 3, 0), afterFire.toLocalDateTime());
    }

    @Test
    void computesYearRestrictedNextExecution() {
        ZonedDateTime next = CronSupport.nextExecution("0 0 0 1 1 ? 2027", BASE).orElseThrow();
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), next.toLocalDateTime());
        assertTrue(CronSupport.nextExecution("0 0 0 1 1 ? 2025", BASE).isEmpty(),
                "a year already past can never fire again");
    }

    @Test
    void previewListIsAscendingAndCapped() {
        List<ZonedDateTime> runs = CronSupport.nextExecutions("0 0/30 * * * ?", BASE, 8);
        assertEquals(8, runs.size());
        for (int i = 1; i < runs.size(); i++) {
            assertTrue(runs.get(i).isAfter(runs.get(i - 1)));
        }
        assertEquals(1, CronSupport.nextExecutions("0 0 3 * * ?", BASE, 0).size(),
                "a non-positive count falls back to one");
        assertTrue(CronSupport.nextExecutions("0 0/5 * * * ?", BASE, 999).size() <= CronSupport.MAX_PREVIEW_COUNT);
    }
}
