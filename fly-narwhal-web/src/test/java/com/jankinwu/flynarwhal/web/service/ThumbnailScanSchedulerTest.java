package com.jankinwu.flynarwhal.web.service;

import com.jankinwu.flynarwhal.web.entity.ThumbnailScanConfig;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scheduling tick of the scan scheduler with an injected clock: the pending
 * fire point must survive the sleep/wake cycle (recomputing "next" after the
 * fire instant passed would silently skip every fire) and a fired point must
 * advance to the following one.
 */
class ThumbnailScanSchedulerTest {

    private static final ZonedDateTime T0 =
            ZonedDateTime.of(2026, 11, 11, 4, 0, 0, 0, ZoneId.systemDefault());

    private ThumbnailScanConfigService configService;
    private ThumbnailScanWorker worker;
    private ThumbnailScanScheduler scheduler;
    private ThumbnailScanConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(ThumbnailScanConfigService.class);
        worker = mock(ThumbnailScanWorker.class);
        scheduler = new ThumbnailScanScheduler(configService, worker);
        config = new ThumbnailScanConfig();
        config.setId(ThumbnailScanConfig.SINGLETON_ID);
        config.setEnabled(true);
        config.setCronExpression("0 0 3 * * ?");
        when(configService.getOrCreate()).thenReturn(config);
        // Never block a scheduler test on a generation wait.
        worker.setGenerationWaitTimeoutMs(0);
        when(configService.readDirs(config)).thenReturn(List.of("/vol1/media"));
        ThumbnailScanRun run = new ThumbnailScanRun();
        run.setId(1L);
        when(configService.createQueuedRun(any())).thenReturn(run);
    }

    @Test
    void waitsUntilTheFirePointInsteadOfFiringEarly() {
        long sleepMs = scheduler.computeSleepAndMaybeFire(T0);
        // Sleep is capped at 60s slices; what matters is that it does not fire.
        assertEquals(60_000L, sleepMs);
        verify(worker, never()).enqueue(any());
    }

    @Test
    void firesExactlyOnceWhenThePendingPointIsReached() {
        scheduler.computeSleepAndMaybeFire(T0);

        // Waking 1ms before the fire point must not fire yet…
        ZonedDateTime justBefore = T0.plusDays(1).withHour(2).withMinute(59)
                .withSecond(59).withNano(999_000_000);
        long sleepMs = scheduler.computeSleepAndMaybeFire(justBefore);
        assertTrue(sleepMs <= 1, "should wait at most the remaining millisecond, got " + sleepMs);
        verify(worker, never()).enqueue(any());

        // …and waking at/after it must fire exactly once.
        ZonedDateTime atFire = T0.plusDays(1).withHour(3).withMinute(0).withSecond(0);
        scheduler.computeSleepAndMaybeFire(atFire);
        verify(worker, times(1)).enqueue(any(ThumbnailScanWorker.ScanRequest.class));

        // The pending point advanced to the following day: the next tick (and a
        // tick a minute later) must not fire again.
        scheduler.computeSleepAndMaybeFire(atFire.plusSeconds(1));
        scheduler.computeSleepAndMaybeFire(atFire.plusMinutes(1));
        verify(worker, times(1)).enqueue(any(ThumbnailScanWorker.ScanRequest.class));
    }

    @Test
    void aHighFrequencyCronFiresOnConsecutiveTicks() {
        config.setCronExpression("0/20 * * * * ?");
        scheduler.computeSleepAndMaybeFire(T0);

        scheduler.computeSleepAndMaybeFire(T0.plusSeconds(20));
        verify(worker, times(1)).enqueue(any());
        scheduler.computeSleepAndMaybeFire(T0.plusSeconds(40));
        verify(worker, times(2)).enqueue(any());
    }

    @Test
    void missedPointsAreSkippedNotReplayed() {
        config.setCronExpression("0/20 * * * * ?");
        scheduler.computeSleepAndMaybeFire(T0);
        // The machine was asleep for 100 seconds: only one catch-up fire, and
        // the pending point jumps to the future.
        long sleepMs = scheduler.computeSleepAndMaybeFire(T0.plusSeconds(100));
        verify(worker, times(1)).enqueue(any());
        assertTrue(sleepMs > 0 && sleepMs <= 20_000, "pending fire must be in the future: " + sleepMs);
    }

    @Test
    void disabledOrInvalidConfigNeverFires() {
        config.setEnabled(false);
        assertEquals(60_000L, scheduler.computeSleepAndMaybeFire(T0));
        config.setEnabled(true);
        config.setCronExpression("garbage");
        assertEquals(60_000L, scheduler.computeSleepAndMaybeFire(T0));
        verify(worker, never()).enqueue(any());
    }

    @Test
    void editingTheCronRecomputesThePendingPoint() {
        scheduler.computeSleepAndMaybeFire(T0);
        config.setCronExpression("0 30 4 * * ?");
        // 04:29 belongs to the NEW schedule's fire at 04:30 — must not fire yet,
        // proving the pending point was recomputed (old cron fired at 03:00).
        scheduler.computeSleepAndMaybeFire(T0.plusMinutes(29));
        verify(worker, never()).enqueue(any());
        scheduler.computeSleepAndMaybeFire(T0.plusMinutes(30));
        verify(worker, times(1)).enqueue(any());
    }

    @Test
    void noDirectoriesMeansNoEnqueue() {
        when(configService.readDirs(config)).thenReturn(List.of());
        config.setCronExpression("0/20 * * * * ?");
        scheduler.computeSleepAndMaybeFire(T0);
        scheduler.computeSleepAndMaybeFire(T0.plusSeconds(20));
        verify(worker, never()).enqueue(any());
    }
}
