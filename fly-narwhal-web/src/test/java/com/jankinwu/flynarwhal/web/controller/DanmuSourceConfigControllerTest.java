package com.jankinwu.flynarwhal.web.controller;

import com.jankinwu.flynarwhal.core.danmu.service.DandanPlayClient;
import com.jankinwu.flynarwhal.web.entity.DanmuSourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the DTO mapping for the settings page. The client prefills its input
 * fields from these values, so a field dropped here silently wipes what the
 * user typed the next time the dialog opens.
 */
class DanmuSourceConfigControllerTest {

    /**
     * The regression this exists for: switching the relay off used to blank the
     * reported URL, so reopening the dialog showed an empty address and looked
     * like the address had been lost — even though the row still held it.
     */
    @Test
    void relayUrlIsReportedEvenWhileDisabled() {
        DanmuSourceConfig row = new DanmuSourceConfig();
        row.setSourceType(DanmuSourceConfig.TYPE_DANDAN_RELAY);
        row.setUrl("https://relay.example/ddp/v1");
        row.setEnabled(false);
        row.setPriority(1);

        DanmuSourceConfigController.DandanDto dto = DanmuSourceConfigController.toDandanDto(row);

        assertEquals("https://relay.example/ddp/v1", dto.getUrl(),
                "a disabled source must still report its stored address");
        assertFalse(dto.isEnabled());
        assertEquals(1, dto.getPriority());
    }

    @Test
    void relayUrlSurvivesAnUnsetAddress() {
        DanmuSourceConfig row = new DanmuSourceConfig();
        row.setUrl(null);
        row.setEnabled(true);

        assertEquals("", DanmuSourceConfigController.toDandanDto(row).getUrl());
    }

    /** Credentials follow the same rule: off, but still shown. */
    @Test
    void credentialsAreReportedEvenWhileDisabled() {
        DanmuSourceConfig row = new DanmuSourceConfig();
        row.setSourceType(DanmuSourceConfig.TYPE_DANDAN_ACCOUNT);
        row.setAppId("my-id");
        row.setAppSecret("my-secret");
        row.setEnabled(false);
        row.setPriority(0);

        DanmuSourceConfigController.DandanAccountDto dto =
                DanmuSourceConfigController.toDandanAccountDto(row);

        assertEquals("my-id", dto.getAppId());
        assertEquals("my-secret", dto.getAppSecret());
        assertFalse(dto.isEnabled());
        assertEquals(0, dto.getPriority());
    }

    /** An unconfigured account reports blanks rather than failing. */
    @Test
    void missingAccountRowYieldsBlanks() {
        DanmuSourceConfigController.DandanAccountDto dto =
                DanmuSourceConfigController.toDandanAccountDto(null);

        assertEquals("", dto.getAppId());
        assertEquals("", dto.getAppSecret());
        assertFalse(dto.isEnabled());
        assertNull(dto.getPriority());
    }

    /** A row that predates the priority column reports null, not zero. */
    @Test
    void unsetPriorityStaysNull() {
        DanmuSourceConfig row = new DanmuSourceConfig();
        row.setUrl("https://relay.example");
        row.setEnabled(true);

        assertNull(DanmuSourceConfigController.toDandanDto(row).getPriority());
        assertTrue(DanmuSourceConfigController.toDandanDto(row).isEnabled());
    }

    // ---- probe messages shown verbatim by the settings page ----

    @Test
    void relayProbeMessages() {
        DanmuSourceConfigController.ProbeResult ok = DanmuSourceConfigController.toProbeResult(
                new DandanPlayClient.ProbeOutcome(true, null),
                "中转服务连接成功", "中转服务连接失败");

        assertTrue(ok.isOk());
        assertEquals("中转服务连接成功", ok.getDetail());
    }

    /**
     * The failure message keeps its prefix and appends the reason, so the user
     * still learns whether the host was unreachable or answered with garbage.
     */
    @Test
    void relayProbeFailureAppendsTheReason() {
        DanmuSourceConfigController.ProbeResult failed = DanmuSourceConfigController.toProbeResult(
                new DandanPlayClient.ProbeOutcome(false, "HTTP 404"),
                "中转服务连接成功", "中转服务连接失败");

        assertFalse(failed.isOk());
        assertEquals("中转服务连接失败：HTTP 404", failed.getDetail());
    }

    @Test
    void officialProbeMessagesMatchTheWording() {
        DanmuSourceConfigController.ProbeResult ok = DanmuSourceConfigController.toProbeResult(
                new DandanPlayClient.ProbeOutcome(true, null),
                "弹弹play 官方服务连接成功", "弹弹play 官方服务连接失败");

        assertTrue(ok.isOk());
        assertEquals("弹弹play 官方服务连接成功", ok.getDetail());
    }

    /** A reason-less failure still reads as the plain failure message. */
    @Test
    void probeFailureWithoutReasonStaysPlain() {
        DanmuSourceConfigController.ProbeResult failed = DanmuSourceConfigController.toProbeResult(
                new DandanPlayClient.ProbeOutcome(false, null),
                "弹弹play 官方服务连接成功", "弹弹play 官方服务连接失败");

        assertFalse(failed.isOk());
        assertEquals("弹弹play 官方服务连接失败", failed.getDetail());
    }
}
