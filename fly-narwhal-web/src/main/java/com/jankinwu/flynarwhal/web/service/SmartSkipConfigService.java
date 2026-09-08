package com.jankinwu.flynarwhal.web.service;

import com.jankinwu.flynarwhal.core.data.SmartSkipConfig;
import com.jankinwu.flynarwhal.web.entity.UserSmartSkipConfig;
import com.jankinwu.flynarwhal.web.mapper.UserSmartSkipConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Per-user SmartSkipConfig persistence. Users without a saved row (and legacy
 * clients that never send a userGuid) get the defaults.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmartSkipConfigService {

    private final UserSmartSkipConfigMapper userSmartSkipConfigMapper;

    public SmartSkipConfig getConfig(String userGuid) {
        if (userGuid == null || userGuid.isBlank()) {
            return SmartSkipConfig.defaultConfig();
        }
        UserSmartSkipConfig row = userSmartSkipConfigMapper.selectById(userGuid);
        if (row == null) {
            return SmartSkipConfig.defaultConfig();
        }
        return row.toConfig();
    }

    public void saveConfig(String userGuid, SmartSkipConfig config) {
        if (userGuid == null || userGuid.isBlank()) {
            throw new IllegalArgumentException("user_guid is required");
        }
        SmartSkipConfig clamped = clamp(config != null ? config : SmartSkipConfig.defaultConfig());
        UserSmartSkipConfig entity = UserSmartSkipConfig.fromConfig(userGuid, clamped);
        LocalDateTime now = LocalDateTime.now();

        UserSmartSkipConfig existing = userSmartSkipConfigMapper.selectById(userGuid);
        if (existing == null) {
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            userSmartSkipConfigMapper.insert(entity);
        } else {
            entity.setCreateTime(existing.getCreateTime());
            entity.setUpdateTime(now);
            userSmartSkipConfigMapper.updateById(entity);
        }
    }

    /** Clamp client-provided values into sane ranges so bad input cannot break analysis. */
    private SmartSkipConfig clamp(SmartSkipConfig c) {
        return c.toBuilder()
                .analysisPercent(clampInt(c.getAnalysisPercent(), 1, 50))
                .analysisLengthLimit(clampInt(c.getAnalysisLengthLimit(), 1, 60))
                .minimumIntroDuration(clampInt(c.getMinimumIntroDuration(), 0, 600))
                .maximumIntroDuration(clampInt(c.getMaximumIntroDuration(), 1, 3600))
                .minimumCreditsDuration(clampInt(c.getMinimumCreditsDuration(), 0, 600))
                .maximumCreditsDuration(clampInt(c.getMaximumCreditsDuration(), 1, 3600))
                .maximumMovieCreditsDuration(clampInt(c.getMaximumMovieCreditsDuration(), 1, 7200))
                .minimumRecapDuration(clampInt(c.getMinimumRecapDuration(), 0, 600))
                .maximumRecapDuration(clampInt(c.getMaximumRecapDuration(), 1, 3600))
                .minimumPreviewDuration(clampInt(c.getMinimumPreviewDuration(), 0, 600))
                .maximumPreviewDuration(clampInt(c.getMaximumPreviewDuration(), 1, 3600))
                .minimumCommercialDuration(clampInt(c.getMinimumCommercialDuration(), 0, 600))
                .maximumCommercialDuration(clampInt(c.getMaximumCommercialDuration(), 1, 3600))
                .silenceDetectionMaximumNoise(clampInt(c.getSilenceDetectionMaximumNoise(), -90, 0))
                .silenceDetectionMinimumDuration(clampDouble(c.getSilenceDetectionMinimumDuration(), 0.05, 5.0))
                .introEndOffset(clampInt(c.getIntroEndOffset(), -60, 60))
                .introStartOffset(clampInt(c.getIntroStartOffset(), -60, 60))
                .creditsEndOffset(clampInt(c.getCreditsEndOffset(), -60, 60))
                .maximumFingerprintPointDifferences(clampInt(c.getMaximumFingerprintPointDifferences(), 0, 32))
                .maximumTimeSkip(clampDouble(c.getMaximumTimeSkip(), 0.5, 10.0))
                .blackFrameMinimumPercentage(clampInt(c.getBlackFrameMinimumPercentage(), 0, 100))
                .blackFrameThreshold(clampInt(c.getBlackFrameThreshold(), 0, 255))
                .build();
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
