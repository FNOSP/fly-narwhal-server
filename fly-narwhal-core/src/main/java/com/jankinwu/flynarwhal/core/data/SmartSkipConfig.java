package com.jankinwu.flynarwhal.core.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-user smart skip analysis configuration.
 *
 * Field names and defaults are aligned with intro-skipper's PluginConfiguration
 * so behavior matches the upstream plugin out of the box. Instances are built
 * from the USER_SMART_SKIP_CONFIG table row for a user; {@link #defaultConfig()}
 * is used when a user has no saved config (including legacy clients that never
 * send one).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SmartSkipConfig {

    // ---- Analysis mode switches ----
    private boolean scanIntroduction;
    private boolean scanCredits;
    private boolean scanRecap;
    private boolean scanPreview;
    private boolean scanCommercial;

    // ---- Queue / fingerprint window ----
    private int analysisPercent;
    private int analysisLengthLimit;

    // ---- Duration limits (seconds) ----
    private int minimumIntroDuration;
    private int maximumIntroDuration;
    private int minimumCreditsDuration;
    private int maximumCreditsDuration;
    private int maximumMovieCreditsDuration;
    private int minimumRecapDuration;
    private int maximumRecapDuration;
    private int minimumPreviewDuration;
    private int maximumPreviewDuration;
    private int minimumCommercialDuration;
    private int maximumCommercialDuration;

    // ---- Boundary adjustment ----
    private boolean adjustIntroBasedOnChapters;
    private boolean adjustIntroBasedOnSilence;
    private int silenceDetectionMaximumNoise;
    private double silenceDetectionMinimumDuration;
    private int introEndOffset;
    private int introStartOffset;
    private int creditsEndOffset;

    // ---- Chapter patterns ----
    private String chapterAnalyzerIntroductionPattern;
    private String chapterAnalyzerEndCreditsPattern;
    private String chapterAnalyzerRecapPattern;
    private String chapterAnalyzerPreviewPattern;
    private String chapterAnalyzerCommercialPattern;

    // ---- Chromaprint / fingerprint ----
    private boolean preferChromaprint;
    private int maximumFingerprintPointDifferences;
    private double maximumTimeSkip;

    // ---- Black frame / credits detection ----
    private boolean useAlternativeBlackFrameAnalyzer;
    private boolean useNewCreditsBlackFrameAnalyzer;
    private boolean detectNonBlackCredits;
    private boolean refineCreditsBoundary;
    private boolean useChapterMarkersBlackFrame;
    private int blackFrameMinimumPercentage;
    private int blackFrameThreshold;

    // ---- Chapter detection ----
    private boolean enableSponsorBlockChapterDetection;
    private boolean detectRecapUsingBlackFrames;
    private boolean fullLengthChapters;

    // ---- Boundary adjustment ----
    private boolean snapToKeyframe;
    private double endSnapThreshold;
    private double adjustWindowInward;
    private double adjustWindowOutward;

    // ---- Content hints ----
    private boolean animeDetection;

    /** Chromaprint fingerprint sample duration in seconds (4096 / 11025 / 3). */
    public static final double SAMPLE_DURATION = 0.1238;

    /** Upper bound of the intro fingerprint window, in seconds. */
    public static final int MAX_INTRO_DURATION = 600;

    /** Default ffmpeg timeout for analysis commands, in seconds. */
    public static final int DEFAULT_TIMEOUT_SECONDS = 60;

    /** Timeout for short probe commands (duration / chapters), in seconds. */
    public static final int PROBE_TIMEOUT_SECONDS = 30;

    public static SmartSkipConfig defaultConfig() {
        return SmartSkipConfig.builder()
                .scanIntroduction(true)
                .scanCredits(true)
                .scanRecap(true)
                .scanPreview(true)
                .scanCommercial(false)
                .analysisPercent(25)
                .analysisLengthLimit(10)
                .minimumIntroDuration(15)
                .maximumIntroDuration(120)
                .minimumCreditsDuration(15)
                .maximumCreditsDuration(450)
                .maximumMovieCreditsDuration(900)
                .minimumRecapDuration(15)
                .maximumRecapDuration(120)
                .minimumPreviewDuration(15)
                .maximumPreviewDuration(120)
                .minimumCommercialDuration(15)
                .maximumCommercialDuration(120)
                .adjustIntroBasedOnChapters(true)
                .adjustIntroBasedOnSilence(true)
                .silenceDetectionMaximumNoise(-50)
                .silenceDetectionMinimumDuration(0.33)
                .introEndOffset(0)
                .introStartOffset(0)
                .creditsEndOffset(0)
                .chapterAnalyzerIntroductionPattern("^(Intro|Introduction|OP|Opening)(?!\\sEnd)(\\s|$)")
                .chapterAnalyzerEndCreditsPattern("^(Credits?|ED|Ending|Outro)(?!\\sEnd)(\\s|$)")
                .chapterAnalyzerRecapPattern("^(Recap|Summary|Previously)(\\s|$)")
                .chapterAnalyzerPreviewPattern("^(Preview|PV|Sneak Peek|Coming Soon)(\\s|$)")
                .chapterAnalyzerCommercialPattern("^(Advertisement|Commercial)(\\s|$)")
                .preferChromaprint(false)
                .maximumFingerprintPointDifferences(6)
                .maximumTimeSkip(3.5)
                .useAlternativeBlackFrameAnalyzer(false)
                .useNewCreditsBlackFrameAnalyzer(true)
                .detectNonBlackCredits(false)
                .refineCreditsBoundary(true)
                .useChapterMarkersBlackFrame(true)
                .blackFrameMinimumPercentage(85)
                .blackFrameThreshold(28)
                .enableSponsorBlockChapterDetection(true)
                .detectRecapUsingBlackFrames(false)
                .fullLengthChapters(false)
                .snapToKeyframe(false)
                .endSnapThreshold(2.0)
                .adjustWindowInward(5.0)
                .adjustWindowOutward(2.0)
                .animeDetection(false)
                .build();
    }

    /** Intro fingerprint window end for an episode of the given duration, in seconds (upstream GetFingerprintRange). */
    public double getIntroFingerprintEnd(double duration) {
        double end = duration * getAnalysisPercent() / 100.0;
        end = Math.min(end, MAX_INTRO_DURATION);
        end = Math.min(end, getAnalysisLengthLimit() * 60.0);
        return Math.max(end, 0);
    }

    /** Credits fingerprint window start for an episode of the given duration (upstream QueueManager). */
    public double getCreditsFingerprintStart(double duration) {
        return getCreditsFingerprintStart(duration, false);
    }

    /**
     * Credits have their own maximum duration in seconds. Upstream deliberately does NOT
     * apply the general analysis percentage here, since it can exclude the actual credits
     * boundary: start = duration - min(duration, MaximumCreditsDuration), or
     * MaximumMovieCreditsDuration for movies.
     */
    public double getCreditsFingerprintStart(double duration, boolean isMovie) {
        if (duration <= 0) {
            return 0;
        }
        double maxCreditsDuration = Math.min(duration,
                isMovie ? getMaximumMovieCreditsDuration() : getMaximumCreditsDuration());
        return Math.max(0, duration - maxCreditsDuration);
    }
}
