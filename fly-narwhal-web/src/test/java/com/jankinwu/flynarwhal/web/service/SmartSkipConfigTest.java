package com.jankinwu.flynarwhal.web.service;

import com.jankinwu.flynarwhal.core.data.SmartSkipConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmartSkipConfigTest {

    @Test
    void defaultConfigMatchesUpstreamDefaults() {
        SmartSkipConfig config = SmartSkipConfig.defaultConfig();
        assertTrue(config.isScanIntroduction());
        assertTrue(config.isScanCredits());
        assertTrue(config.isScanRecap());
        assertTrue(config.isScanPreview());
        assertFalse(config.isScanCommercial());
        assertEquals(15, config.getMinimumIntroDuration());
        assertEquals(120, config.getMaximumIntroDuration());
        assertEquals(15, config.getMinimumCreditsDuration());
        assertEquals(450, config.getMaximumCreditsDuration());
        assertEquals(900, config.getMaximumMovieCreditsDuration());
        assertEquals(6, config.getMaximumFingerprintPointDifferences());
        assertEquals(3.5, config.getMaximumTimeSkip(), 1e-9);
        assertEquals(85, config.getBlackFrameMinimumPercentage());
        assertEquals(28, config.getBlackFrameThreshold());
        assertEquals(25, config.getAnalysisPercent());
        assertEquals(10, config.getAnalysisLengthLimit());
        assertFalse(config.isPreferChromaprint());
        assertFalse(config.isUseAlternativeBlackFrameAnalyzer());
    }

    @Test
    void fingerprintWindowsFollowAnalysisPercentAndCap() {
        SmartSkipConfig config = SmartSkipConfig.defaultConfig();
        // 40-minute episode: 25% = 600s, capped at 600 and 10*60
        assertEquals(600, config.getIntroFingerprintEnd(2400), 1e-9);
        // short episode: 25% wins
        assertEquals(300, config.getIntroFingerprintEnd(1200), 1e-9);
    }

    @Test
    void creditsWindowFollowsMaximumCreditsDurationNotAnalysisPercent() {
        SmartSkipConfig config = SmartSkipConfig.defaultConfig();
        // Upstream: credits start = duration - min(duration, MaximumCreditsDuration=450).
        // The analysis percentage must NOT shrink the credits window.
        assertEquals(1950, config.getCreditsFingerprintStart(2400), 1e-9);
        // Episode shorter than the maximum: window starts at 0.
        assertEquals(50, config.getCreditsFingerprintStart(500), 1e-9);
        assertEquals(0, config.getCreditsFingerprintStart(0), 1e-9);
        // Movies use MaximumMovieCreditsDuration (900).
        assertEquals(6300, config.getCreditsFingerprintStart(7200, true), 1e-9);
        assertEquals(0, config.getCreditsFingerprintStart(800, true), 1e-9);
    }

    @Test
    void entityRoundTripPreservesValues() {
        SmartSkipConfig config = SmartSkipConfig.defaultConfig().toBuilder()
                .scanCredits(false)
                .maximumIntroDuration(90)
                .maximumTimeSkip(4.5)
                .build();
        com.jankinwu.flynarwhal.web.entity.UserSmartSkipConfig entity =
                com.jankinwu.flynarwhal.web.entity.UserSmartSkipConfig.fromConfig("user-1", config);
        SmartSkipConfig roundTripped = entity.toConfig();

        assertFalse(roundTripped.isScanCredits());
        assertEquals(90, roundTripped.getMaximumIntroDuration());
        assertEquals(4.5, roundTripped.getMaximumTimeSkip(), 1e-9);
        // untouched fields fall back to defaults
        assertEquals(15, roundTripped.getMinimumIntroDuration());
        assertEquals(450, roundTripped.getMaximumCreditsDuration());
    }

    @Test
    void emptyEntityReturnsDefaults() {
        com.jankinwu.flynarwhal.web.entity.UserSmartSkipConfig entity =
                new com.jankinwu.flynarwhal.web.entity.UserSmartSkipConfig();
        entity.setUserGuid("user-2");
        assertEquals(SmartSkipConfig.defaultConfig(), entity.toConfig());
    }
}
