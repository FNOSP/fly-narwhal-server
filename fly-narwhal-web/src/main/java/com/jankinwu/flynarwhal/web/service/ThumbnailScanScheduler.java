package com.jankinwu.flynarwhal.web.service;

import com.jankinwu.flynarwhal.web.entity.ThumbnailScanConfig;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRun;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Cron driver of the scheduled thumbnail scan: one daemon thread recomputes the
 * next fire time from the config row (cron-utils, Quartz dialect) and sleeps
 * until then in bounded slices, enqueuing into {@link ThumbnailScanWorker} when
 * the moment arrives. Deliberately a hand-rolled thread in the style of
 * ThumbnailService/AnalysisService rather than @Scheduled — the cron is dynamic
 * (edited at runtime) and the native image stays free of scheduling proxies.
 *
 * <p>Starts on ApplicationReadyEvent, not @PostConstruct: the migration runner
 * is an ApplicationRunner, so the config table only exists after context refresh.
 * A missed fire time (server asleep/off) is not replayed; the next cron match runs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailScanScheduler {

    /** Sleep cap so config edits and clock drift are picked up within a minute regardless of notifies. */
    private static final long MAX_SLEEP_MS = 60_000;
    /** Pause after handing a fire to the worker before recomputing, avoiding a same-second busy loop. */
    private static final long POST_FIRE_SLEEP_MS = 1_000;

    private final ThumbnailScanConfigService configService;
    private final ThumbnailScanWorker worker;

    private final Object lock = new Object();
    private volatile boolean running;
    private volatile boolean started;

    /** Scheduler-thread-only state: the fire point being waited for, and the cron it came from. */
    private ZonedDateTime pendingFire;
    private String pendingFireCron;

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        running = true;
        Thread thread = new Thread(this::loop, "ThumbnailScanSchedulerThread");
        thread.setDaemon(true);
        thread.start();
        log.info("Thumbnail scan scheduler started");
    }

    @PreDestroy
    public void stop() {
        running = false;
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    /** Woken by config saves so a new cron takes effect without waiting out the sleep slice. */
    @EventListener
    public void onConfigChanged(ThumbnailScanConfigChangedEvent event) {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    private void loop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            long sleepMs = MAX_SLEEP_MS;
            try {
                sleepMs = computeSleepAndMaybeFire();
            } catch (Exception e) {
                log.warn("Thumbnail scan scheduler iteration failed: {}", e.getMessage());
            }
            try {
                synchronized (lock) {
                    if (running) {
                        lock.wait(sleepMs);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Thumbnail scan scheduler stopped");
    }

    /** Milliseconds to sleep before re-evaluating; enqueues a scan when the fire time has arrived. */
    private long computeSleepAndMaybeFire() {
        return computeSleepAndMaybeFire(ZonedDateTime.now());
    }

    /** One scheduling tick at a given instant. Package-visible for tests. */
    long computeSleepAndMaybeFire(ZonedDateTime now) {
        ThumbnailScanConfig config = configService.getOrCreate();
        if (!Boolean.TRUE.equals(config.getEnabled()) || config.getCronExpression() == null
                || config.getCronExpression().isBlank()) {
            pendingFire = null;
            pendingFireCron = null;
            return MAX_SLEEP_MS;
        }
        String cron = config.getCronExpression();
        if (!CronSupport.isValid(cron)) {
            log.warn("Stored thumbnail scan cron is invalid, scheduler idle: {}", cron);
            pendingFire = null;
            pendingFireCron = null;
            return MAX_SLEEP_MS;
        }
        // A changed expression invalidates the pending fire point.
        if (pendingFire != null && !cron.equals(pendingFireCron)) {
            pendingFire = null;
        }
        if (pendingFire == null) {
            pendingFireCron = cron;
            pendingFire = CronSupport.nextExecution(cron, now).orElse(null);
            if (pendingFire == null) {
                // e.g. a year-restricted expression whose year has passed.
                return MAX_SLEEP_MS;
            }
        }
        long waitMs = Duration.between(now, pendingFire).toMillis();
        if (waitMs > 0) {
            return Math.min(waitMs, MAX_SLEEP_MS);
        }
        // The pending fire point has arrived (now >= pendingFire). Enqueue first,
        // then advance: recomputing from `now` before firing would skip the point
        // entirely, because nextExecution is strictly in the future.
        List<String> dirs = configService.readDirs(config);
        if (!dirs.isEmpty()) {
            // Same record discipline as a manual trigger: the run row exists
            // (QUEUED) before the worker runs it, so history is never implicit.
            ThumbnailScanRun run = configService.createQueuedRun(ThumbnailScanWorker.ScanRequest.SOURCE_SCHEDULED);
            worker.enqueue(new ThumbnailScanWorker.ScanRequest(
                    ThumbnailScanWorker.ScanRequest.SOURCE_SCHEDULED, dirs, run.getId()));
        }
        // Advance from the fired point to keep the cadence; skip points already
        // in the past (a missed fire is never replayed).
        ZonedDateTime following = CronSupport.nextExecution(cron, pendingFire).orElse(null);
        pendingFire = (following != null && following.isAfter(now))
                ? following
                : CronSupport.nextExecution(cron, now).orElse(null);
        return POST_FIRE_SLEEP_MS;
    }
}
