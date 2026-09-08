package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jankinwu.flynarwhal.core.data.SmartSkipConfig;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Per-user smart skip configuration. Every column is nullable; a null column
 * means "use the SmartSkipConfig default" so new config fields added later do
 * not require backfilling existing rows.
 */
@Data
@TableName("USER_SMART_SKIP_CONFIG")
public class UserSmartSkipConfig {

    @TableId(value = "user_guid", type = IdType.INPUT)
    private String userGuid;

    private Boolean scanIntroduction;
    private Boolean scanCredits;
    private Boolean scanRecap;
    private Boolean scanPreview;
    private Boolean scanCommercial;

    private Integer analysisPercent;
    private Integer analysisLengthLimit;

    private Integer minimumIntroDuration;
    private Integer maximumIntroDuration;
    private Integer minimumCreditsDuration;
    private Integer maximumCreditsDuration;
    private Integer maximumMovieCreditsDuration;
    private Integer minimumRecapDuration;
    private Integer maximumRecapDuration;
    private Integer minimumPreviewDuration;
    private Integer maximumPreviewDuration;
    private Integer minimumCommercialDuration;
    private Integer maximumCommercialDuration;

    private Boolean adjustIntroBasedOnChapters;
    private Boolean adjustIntroBasedOnSilence;
    private Integer silenceDetectionMaximumNoise;
    private Double silenceDetectionMinimumDuration;
    private Integer introEndOffset;
    private Integer introStartOffset;
    private Integer creditsEndOffset;

    private String chapterAnalyzerIntroductionPattern;
    private String chapterAnalyzerEndCreditsPattern;
    private String chapterAnalyzerRecapPattern;
    private String chapterAnalyzerPreviewPattern;
    private String chapterAnalyzerCommercialPattern;

    private Boolean preferChromaprint;
    private Integer maximumFingerprintPointDifferences;
    private Double maximumTimeSkip;

    private Boolean useAlternativeBlackFrameAnalyzer;
    private Boolean useNewCreditsBlackFrameAnalyzer;
    private Boolean detectNonBlackCredits;
    private Boolean refineCreditsBoundary;
    private Boolean useChapterMarkersBlackFrame;
    private Integer blackFrameMinimumPercentage;
    private Integer blackFrameThreshold;

    private Boolean enableSponsorBlockChapterDetection;
    private Boolean detectRecapUsingBlackFrames;
    private Boolean fullLengthChapters;

    private Boolean snapToKeyframe;
    private Double endSnapThreshold;
    private Double adjustWindowInward;
    private Double adjustWindowOutward;

    private Boolean animeDetection;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** Merge the stored (nullable) values over the defaults. */
    public SmartSkipConfig toConfig() {
        SmartSkipConfig defaults = SmartSkipConfig.defaultConfig();
        SmartSkipConfig.SmartSkipConfigBuilder builder = defaults.toBuilder()
                .scanIntroduction(scanIntroduction != null ? scanIntroduction : defaults.isScanIntroduction())
                .scanCredits(scanCredits != null ? scanCredits : defaults.isScanCredits())
                .scanRecap(scanRecap != null ? scanRecap : defaults.isScanRecap())
                .scanPreview(scanPreview != null ? scanPreview : defaults.isScanPreview())
                .scanCommercial(scanCommercial != null ? scanCommercial : defaults.isScanCommercial())
                .analysisPercent(analysisPercent != null ? analysisPercent : defaults.getAnalysisPercent())
                .analysisLengthLimit(analysisLengthLimit != null ? analysisLengthLimit : defaults.getAnalysisLengthLimit())
                .minimumIntroDuration(minimumIntroDuration != null ? minimumIntroDuration : defaults.getMinimumIntroDuration())
                .maximumIntroDuration(maximumIntroDuration != null ? maximumIntroDuration : defaults.getMaximumIntroDuration())
                .minimumCreditsDuration(minimumCreditsDuration != null ? minimumCreditsDuration : defaults.getMinimumCreditsDuration())
                .maximumCreditsDuration(maximumCreditsDuration != null ? maximumCreditsDuration : defaults.getMaximumCreditsDuration())
                .maximumMovieCreditsDuration(maximumMovieCreditsDuration != null ? maximumMovieCreditsDuration : defaults.getMaximumMovieCreditsDuration())
                .minimumRecapDuration(minimumRecapDuration != null ? minimumRecapDuration : defaults.getMinimumRecapDuration())
                .maximumRecapDuration(maximumRecapDuration != null ? maximumRecapDuration : defaults.getMaximumRecapDuration())
                .minimumPreviewDuration(minimumPreviewDuration != null ? minimumPreviewDuration : defaults.getMinimumPreviewDuration())
                .maximumPreviewDuration(maximumPreviewDuration != null ? maximumPreviewDuration : defaults.getMaximumPreviewDuration())
                .minimumCommercialDuration(minimumCommercialDuration != null ? minimumCommercialDuration : defaults.getMinimumCommercialDuration())
                .maximumCommercialDuration(maximumCommercialDuration != null ? maximumCommercialDuration : defaults.getMaximumCommercialDuration())
                .adjustIntroBasedOnChapters(adjustIntroBasedOnChapters != null ? adjustIntroBasedOnChapters : defaults.isAdjustIntroBasedOnChapters())
                .adjustIntroBasedOnSilence(adjustIntroBasedOnSilence != null ? adjustIntroBasedOnSilence : defaults.isAdjustIntroBasedOnSilence())
                .silenceDetectionMaximumNoise(silenceDetectionMaximumNoise != null ? silenceDetectionMaximumNoise : defaults.getSilenceDetectionMaximumNoise())
                .silenceDetectionMinimumDuration(silenceDetectionMinimumDuration != null ? silenceDetectionMinimumDuration : defaults.getSilenceDetectionMinimumDuration())
                .introEndOffset(introEndOffset != null ? introEndOffset : defaults.getIntroEndOffset())
                .introStartOffset(introStartOffset != null ? introStartOffset : defaults.getIntroStartOffset())
                .creditsEndOffset(creditsEndOffset != null ? creditsEndOffset : defaults.getCreditsEndOffset())
                .chapterAnalyzerIntroductionPattern(chapterAnalyzerIntroductionPattern != null ? chapterAnalyzerIntroductionPattern : defaults.getChapterAnalyzerIntroductionPattern())
                .chapterAnalyzerEndCreditsPattern(chapterAnalyzerEndCreditsPattern != null ? chapterAnalyzerEndCreditsPattern : defaults.getChapterAnalyzerEndCreditsPattern())
                .chapterAnalyzerRecapPattern(chapterAnalyzerRecapPattern != null ? chapterAnalyzerRecapPattern : defaults.getChapterAnalyzerRecapPattern())
                .chapterAnalyzerPreviewPattern(chapterAnalyzerPreviewPattern != null ? chapterAnalyzerPreviewPattern : defaults.getChapterAnalyzerPreviewPattern())
                .chapterAnalyzerCommercialPattern(chapterAnalyzerCommercialPattern != null ? chapterAnalyzerCommercialPattern : defaults.getChapterAnalyzerCommercialPattern())
                .preferChromaprint(preferChromaprint != null ? preferChromaprint : defaults.isPreferChromaprint())
                .maximumFingerprintPointDifferences(maximumFingerprintPointDifferences != null ? maximumFingerprintPointDifferences : defaults.getMaximumFingerprintPointDifferences())
                .maximumTimeSkip(maximumTimeSkip != null ? maximumTimeSkip : defaults.getMaximumTimeSkip())
                .useAlternativeBlackFrameAnalyzer(useAlternativeBlackFrameAnalyzer != null ? useAlternativeBlackFrameAnalyzer : defaults.isUseAlternativeBlackFrameAnalyzer())
                .useNewCreditsBlackFrameAnalyzer(useNewCreditsBlackFrameAnalyzer != null ? useNewCreditsBlackFrameAnalyzer : defaults.isUseNewCreditsBlackFrameAnalyzer())
                .detectNonBlackCredits(detectNonBlackCredits != null ? detectNonBlackCredits : defaults.isDetectNonBlackCredits())
                .refineCreditsBoundary(refineCreditsBoundary != null ? refineCreditsBoundary : defaults.isRefineCreditsBoundary())
                .useChapterMarkersBlackFrame(useChapterMarkersBlackFrame != null ? useChapterMarkersBlackFrame : defaults.isUseChapterMarkersBlackFrame())
                .blackFrameMinimumPercentage(blackFrameMinimumPercentage != null ? blackFrameMinimumPercentage : defaults.getBlackFrameMinimumPercentage())
                .blackFrameThreshold(blackFrameThreshold != null ? blackFrameThreshold : defaults.getBlackFrameThreshold())
                .enableSponsorBlockChapterDetection(enableSponsorBlockChapterDetection != null ? enableSponsorBlockChapterDetection : defaults.isEnableSponsorBlockChapterDetection())
                .detectRecapUsingBlackFrames(detectRecapUsingBlackFrames != null ? detectRecapUsingBlackFrames : defaults.isDetectRecapUsingBlackFrames())
                .fullLengthChapters(fullLengthChapters != null ? fullLengthChapters : defaults.isFullLengthChapters())
                .snapToKeyframe(snapToKeyframe != null ? snapToKeyframe : defaults.isSnapToKeyframe())
                .endSnapThreshold(endSnapThreshold != null ? endSnapThreshold : defaults.getEndSnapThreshold())
                .adjustWindowInward(adjustWindowInward != null ? adjustWindowInward : defaults.getAdjustWindowInward())
                .adjustWindowOutward(adjustWindowOutward != null ? adjustWindowOutward : defaults.getAdjustWindowOutward())
                .animeDetection(animeDetection != null ? animeDetection : defaults.isAnimeDetection());
        return builder.build();
    }

    /** Build the stored row from a config; null fields fall back to defaults on read. */
    public static UserSmartSkipConfig fromConfig(String userGuid, SmartSkipConfig config) {
        UserSmartSkipConfig entity = new UserSmartSkipConfig();
        entity.setUserGuid(userGuid);
        entity.setScanIntroduction(config.isScanIntroduction());
        entity.setScanCredits(config.isScanCredits());
        entity.setScanRecap(config.isScanRecap());
        entity.setScanPreview(config.isScanPreview());
        entity.setScanCommercial(config.isScanCommercial());
        entity.setAnalysisPercent(config.getAnalysisPercent());
        entity.setAnalysisLengthLimit(config.getAnalysisLengthLimit());
        entity.setMinimumIntroDuration(config.getMinimumIntroDuration());
        entity.setMaximumIntroDuration(config.getMaximumIntroDuration());
        entity.setMinimumCreditsDuration(config.getMinimumCreditsDuration());
        entity.setMaximumCreditsDuration(config.getMaximumCreditsDuration());
        entity.setMaximumMovieCreditsDuration(config.getMaximumMovieCreditsDuration());
        entity.setMinimumRecapDuration(config.getMinimumRecapDuration());
        entity.setMaximumRecapDuration(config.getMaximumRecapDuration());
        entity.setMinimumPreviewDuration(config.getMinimumPreviewDuration());
        entity.setMaximumPreviewDuration(config.getMaximumPreviewDuration());
        entity.setMinimumCommercialDuration(config.getMinimumCommercialDuration());
        entity.setMaximumCommercialDuration(config.getMaximumCommercialDuration());
        entity.setAdjustIntroBasedOnChapters(config.isAdjustIntroBasedOnChapters());
        entity.setAdjustIntroBasedOnSilence(config.isAdjustIntroBasedOnSilence());
        entity.setSilenceDetectionMaximumNoise(config.getSilenceDetectionMaximumNoise());
        entity.setSilenceDetectionMinimumDuration(config.getSilenceDetectionMinimumDuration());
        entity.setIntroEndOffset(config.getIntroEndOffset());
        entity.setIntroStartOffset(config.getIntroStartOffset());
        entity.setCreditsEndOffset(config.getCreditsEndOffset());
        entity.setChapterAnalyzerIntroductionPattern(config.getChapterAnalyzerIntroductionPattern());
        entity.setChapterAnalyzerEndCreditsPattern(config.getChapterAnalyzerEndCreditsPattern());
        entity.setChapterAnalyzerRecapPattern(config.getChapterAnalyzerRecapPattern());
        entity.setChapterAnalyzerPreviewPattern(config.getChapterAnalyzerPreviewPattern());
        entity.setChapterAnalyzerCommercialPattern(config.getChapterAnalyzerCommercialPattern());
        entity.setPreferChromaprint(config.isPreferChromaprint());
        entity.setMaximumFingerprintPointDifferences(config.getMaximumFingerprintPointDifferences());
        entity.setMaximumTimeSkip(config.getMaximumTimeSkip());
        entity.setUseAlternativeBlackFrameAnalyzer(config.isUseAlternativeBlackFrameAnalyzer());
        entity.setUseNewCreditsBlackFrameAnalyzer(config.isUseNewCreditsBlackFrameAnalyzer());
        entity.setDetectNonBlackCredits(config.isDetectNonBlackCredits());
        entity.setRefineCreditsBoundary(config.isRefineCreditsBoundary());
        entity.setUseChapterMarkersBlackFrame(config.isUseChapterMarkersBlackFrame());
        entity.setBlackFrameMinimumPercentage(config.getBlackFrameMinimumPercentage());
        entity.setBlackFrameThreshold(config.getBlackFrameThreshold());
        entity.setEnableSponsorBlockChapterDetection(config.isEnableSponsorBlockChapterDetection());
        entity.setDetectRecapUsingBlackFrames(config.isDetectRecapUsingBlackFrames());
        entity.setFullLengthChapters(config.isFullLengthChapters());
        entity.setSnapToKeyframe(config.isSnapToKeyframe());
        entity.setEndSnapThreshold(config.getEndSnapThreshold());
        entity.setAdjustWindowInward(config.getAdjustWindowInward());
        entity.setAdjustWindowOutward(config.getAdjustWindowOutward());
        entity.setAnimeDetection(config.isAnimeDetection());
        return entity;
    }
}
