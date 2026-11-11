package com.jankinwu.flynarwhal.web.service;

/**
 * Published after the scan configuration row changes so the scheduler thread
 * wakes up and recomputes the next fire time immediately instead of at the
 * next 60s polling boundary. A plain object event — no payload needed, the
 * scheduler re-reads the row from the database.
 */
public record ThumbnailScanConfigChangedEvent() {
}
