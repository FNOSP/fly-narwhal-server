package com.jankinwu.flynarwhal.web.service;

import com.jankinwu.flynarwhal.core.danmu.repository.DanmuSourceConfigProvider;
import com.jankinwu.flynarwhal.web.entity.DanmuSourceConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the URL validation/normalization shared by the dandan relay and
 * fallback server save paths, plus the priority order the two dandanplay
 * channels are searched in.
 */
class DanmuSourceConfigServiceTest {

    @Test
    void normalizesValidUrls() {
        assertEquals("https://dmku.hls.one",
                DanmuSourceConfigService.normalizeUrl("https://dmku.hls.one/", false));
        assertEquals("https://api.danmaku.weeblify.app/ddp/v1",
                DanmuSourceConfigService.normalizeUrl("  https://api.danmaku.weeblify.app/ddp/v1///  ", false));
        assertEquals("http://192.168.1.7:9321",
                DanmuSourceConfigService.normalizeUrl("http://192.168.1.7:9321/", false));
    }

    @Test
    void rejectsInvalidUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> DanmuSourceConfigService.normalizeUrl("ftp://x.example", false));
        assertThrows(IllegalArgumentException.class,
                () -> DanmuSourceConfigService.normalizeUrl("dmku.hls.one", false));
        assertThrows(IllegalArgumentException.class,
                () -> DanmuSourceConfigService.normalizeUrl("https://", false));
        assertThrows(IllegalArgumentException.class,
                () -> DanmuSourceConfigService.normalizeUrl("", false));
        assertThrows(IllegalArgumentException.class,
                () -> DanmuSourceConfigService.normalizeUrl(null, false));
    }

    @Test
    void blankAllowedOnlyForDandanDisable() {
        assertEquals("", DanmuSourceConfigService.normalizeUrl("", true));
        assertEquals("", DanmuSourceConfigService.normalizeUrl("   ", true));
        assertEquals("", DanmuSourceConfigService.normalizeUrl(null, true));
        // Non-blank values still go through full validation.
        assertTrue(DanmuSourceConfigService.normalizeUrl("https://a.example", true).equals("https://a.example"));
        assertThrows(IllegalArgumentException.class,
                () -> DanmuSourceConfigService.normalizeUrl("javascript:alert(1)", true));
    }

    // ---- search order between the two dandanplay channels ----

    /**
     * The whole point of the priority column: whoever the client marked
     * "preferred" is searched first, and the other one follows.
     */
    @Test
    void orderFollowsTheStoredPreference() {
        assertEquals(List.of(DanmuSourceConfigProvider.SOURCE_OFFICIAL,
                        DanmuSourceConfigProvider.SOURCE_RELAY),
                order(relayRow("https://relay.example", true, 1),
                        accountRow("id", "secret", true, 0)),
                "official marked preferred must be first");

        assertEquals(List.of(DanmuSourceConfigProvider.SOURCE_RELAY,
                        DanmuSourceConfigProvider.SOURCE_OFFICIAL),
                order(relayRow("https://relay.example", true, 0),
                        accountRow("id", "secret", true, 1)),
                "relay marked preferred must be first");
    }

    /**
     * A null priority is a row from before the column existed (or one that was
     * never given a preference), and must keep the historical official-first
     * rule rather than flipping the order for existing deployments.
     */
    @Test
    void nullPriorityKeepsOfficialFirst() {
        assertEquals(List.of(DanmuSourceConfigProvider.SOURCE_OFFICIAL,
                        DanmuSourceConfigProvider.SOURCE_RELAY),
                order(relayRow("https://relay.example", true, null),
                        accountRow("id", "secret", true, null)));
    }

    /** A channel that is off, or on without what it needs, is skipped. */
    @Test
    void unusableChannelsAreLeftOut() {
        // Relay disabled, official complete and on.
        assertEquals(List.of(DanmuSourceConfigProvider.SOURCE_OFFICIAL),
                order(relayRow("https://relay.example", false, 0),
                        accountRow("id", "secret", true, 1)));

        // Official switched on but with no credentials: not in the chain.
        assertEquals(List.of(DanmuSourceConfigProvider.SOURCE_RELAY),
                order(relayRow("https://relay.example", true, 1),
                        accountRow("", "", true, 0)));

        // Relay on with a blank address: same treatment as missing credentials.
        assertEquals(List.of(DanmuSourceConfigProvider.SOURCE_OFFICIAL),
                order(relayRow("", true, 0),
                        accountRow("id", "secret", true, 1)));
    }

    @Test
    void nothingEnabledYieldsAnEmptyChain() {
        assertTrue(order(relayRow("https://relay.example", false, 0),
                accountRow("id", "secret", false, 1)).isEmpty());
        assertTrue(order(null, null).isEmpty());
    }

    /** Enabled-but-unusable on both sides still yields an empty chain. */
    @Test
    void bothEnabledButNeitherUsableYieldsAnEmptyChain() {
        assertTrue(order(relayRow("", true, 0), accountRow("", "", true, 1)).isEmpty());
    }

    /** With no DB row at all, the static relay setting decides. */
    @Test
    void staticRelaySeedsTheChainWhenNoRowExists() {
        assertEquals(List.of(DanmuSourceConfigProvider.SOURCE_RELAY),
                DanmuSourceConfigService.resolveDandanSourceOrder(
                        null, null, "https://static.example"));
        assertTrue(DanmuSourceConfigService.resolveDandanSourceOrder(null, null, "")
                .isEmpty());
    }

    private static List<String> order(DanmuSourceConfig relay, DanmuSourceConfig account) {
        return DanmuSourceConfigService.resolveDandanSourceOrder(account, relay, "");
    }

    private static DanmuSourceConfig relayRow(String url, boolean enabled, Integer priority) {
        return row(DanmuSourceConfig.TYPE_DANDAN_RELAY, url, enabled, priority, null, null);
    }

    private static DanmuSourceConfig accountRow(String appId, String appSecret,
                                                boolean enabled, Integer priority) {
        return row(DanmuSourceConfig.TYPE_DANDAN_ACCOUNT, null, enabled, priority, appId, appSecret);
    }

    private static DanmuSourceConfig row(String type, String url, boolean enabled, Integer priority,
                                         String appId, String appSecret) {
        DanmuSourceConfig row = new DanmuSourceConfig();
        row.setSourceType(type);
        row.setUrl(url);
        row.setEnabled(enabled);
        row.setPriority(priority);
        row.setAppId(appId);
        row.setAppSecret(appSecret);
        return row;
    }
}
