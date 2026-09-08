package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jankinwu.flynarwhal.core.analyzer.AnalyzerFactory;
import com.jankinwu.flynarwhal.core.analyzer.MediaFileAnalyzer;
import com.jankinwu.flynarwhal.core.data.*;
import com.jankinwu.flynarwhal.core.dto.request.EpisodeDetailRequest;
import com.jankinwu.flynarwhal.core.dto.response.EpisodeSegmentsResponse;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import com.jankinwu.flynarwhal.core.scanner.MediaFileScanner;
import com.jankinwu.flynarwhal.web.entity.EpisodeSegment;
import com.jankinwu.flynarwhal.web.entity.TvSeasonInfo;
import com.jankinwu.flynarwhal.web.entity.UserSmartSkipConfig;
import com.jankinwu.flynarwhal.web.mapper.DbVersionMapper;
import com.jankinwu.flynarwhal.web.mapper.EpisodeSegmentMapper;
import com.jankinwu.flynarwhal.web.mapper.TvSeasonInfoMapper;
import com.jankinwu.flynarwhal.web.mapper.UserSmartSkipConfigMapper;
import com.jankinwu.flynarwhal.web.mapstruct.AnalysisEntityMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

@Service
@Slf4j
@Import({
    AnalyzerFactory.class,
    MediaFileScanner.class
})
public class AnalysisService {

    private final TvSeasonInfoMapper tvSeasonInfoMapper;
    private final EpisodeSegmentMapper episodeSegmentMapper;
    private final DbVersionMapper dbVersionMapper;
    private final UserSmartSkipConfigMapper userSmartSkipConfigMapper;
    private final AnalyzerFactory analyzerFactory;
    private final MediaFileScanner mediaFileScanner;
    private final FFmpegWrapper ffmpegWrapper;
    private final TransactionTemplate transactionTemplate;
    private final AnalysisEntityMapper analysisEntityMapper;
    private final BlockingDeque<AnalyzeJob> analyzeJobQueue = new LinkedBlockingDeque<>();

    @PostConstruct
    public void init() {
        Thread thread = new Thread(this::processQueue, "AnalysisThread");
        thread.setDaemon(true);
        thread.start();
    }

    public AnalysisService(TvSeasonInfoMapper tvSeasonInfoMapper,
                           EpisodeSegmentMapper episodeSegmentMapper,
                           DbVersionMapper dbVersionMapper,
                           UserSmartSkipConfigMapper userSmartSkipConfigMapper,
                           AnalyzerFactory analyzerFactory,
                           MediaFileScanner mediaFileScanner,
                           TransactionTemplate transactionTemplate,
                           AnalysisEntityMapper analysisEntityMapper) {
        this.tvSeasonInfoMapper = tvSeasonInfoMapper;
        this.episodeSegmentMapper = episodeSegmentMapper;
        this.dbVersionMapper = dbVersionMapper;
        this.userSmartSkipConfigMapper = userSmartSkipConfigMapper;
        this.analyzerFactory = analyzerFactory;
        this.mediaFileScanner = mediaFileScanner;
        this.ffmpegWrapper = new FFmpegWrapper();
        this.transactionTemplate = transactionTemplate;
        this.analysisEntityMapper = analysisEntityMapper;
    }

    private void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                AnalyzeJob job = analyzeJobQueue.takeFirst();
                try {
                    analyzeSeasonInternal(job.seasonGuid, job.seasonFolderPath, job.episodes, job.tvTitle, job.seasonNumber, job.userGuid);
                } catch (Exception e) {
                    log.error("Error analyzing season internal", e);
                    updateAnalysisStatus(job.seasonGuid, AnalysisStatus.FAILED);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing analyze job", e);
            }
        }
    }

    public int enqueueAnalyzeSeason(String seasonGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes,
                                    String tvTitle, Integer seasonNumber, String userGuid) {
        List<EpisodeDetailRequest> safeEpisodes = episodes == null ? List.of() : List.copyOf(episodes);
        registerPending(seasonGuid, seasonFolderPath, tvTitle, seasonNumber, safeEpisodes);
        enqueueJob(seasonGuid, seasonFolderPath, safeEpisodes, tvTitle, seasonNumber, userGuid);
        return analyzeJobQueue.size();
    }

    private void enqueueJob(String seasonGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes,
                            String tvTitle, Integer seasonNumber, String userGuid) {
        analyzeJobQueue.addLast(new AnalyzeJob(seasonGuid, seasonFolderPath, episodes, LocalDateTime.now(), tvTitle, seasonNumber, userGuid));
    }

    private void registerPending(String seasonGuid, String seasonFolderPath, String tvTitle, Integer seasonNumber, List<EpisodeDetailRequest> episodes) {
        transactionTemplate.executeWithoutResult(tx -> {
            upsertSeries(seasonGuid, seasonFolderPath, tvTitle, seasonNumber, AnalysisStatus.PENDING);
            upsertEpisodeSegmentsFromRequest(seasonGuid, episodes, AnalysisStatus.PENDING);
        });
    }

    /**
     * Load the requesting user's smart skip config; legacy clients send no
     * userGuid and any user without a saved row gets the defaults.
     */
    private SmartSkipConfig loadConfig(String userGuid) {
        if (userGuid == null || userGuid.isBlank()) {
            return SmartSkipConfig.defaultConfig();
        }
        try {
            UserSmartSkipConfig row = userSmartSkipConfigMapper.selectById(userGuid);
            if (row != null) {
                return row.toConfig();
            }
        } catch (Exception e) {
            log.error("Failed to load smart skip config for user {}, falling back to defaults", userGuid, e);
        }
        return SmartSkipConfig.defaultConfig();
    }

    private AnalysisStatus getSeasonAnalysisStatus(String seasonGuid) {
        TvSeasonInfo series = tvSeasonInfoMapper.selectById(seasonGuid);
        return series != null ? series.getStatus() : null;
    }

    private AnalysisStatus getEpisodeAnalysisStatus(String episodeGuid) {
        EpisodeSegment segment = findEpisodeSegmentByGuid(episodeGuid);
        return segment != null ? segment.getStatus() : null;
    }

    public AnalysisStatus getStatus(String type, String guid) {
        if ("EPISODE".equalsIgnoreCase(type)) {
            if (guid == null || guid.isBlank()) {
                throw new IllegalArgumentException("episodeGuid is required when type=EPISODE");
            }
            return getEpisodeAnalysisStatus(guid);
        }

        if (guid == null || guid.isBlank()) {
            throw new IllegalArgumentException("seasonGuid is required when type=SEASON");
        }
        return getSeasonAnalysisStatus(guid);
    }

    public void updateAnalysisStatus(String seasonGuid, AnalysisStatus status) {
        TvSeasonInfo series = new TvSeasonInfo();
        series.setSeasonGuid(seasonGuid);
        series.setStatus(status);
        series.setUpdateTime(LocalDateTime.now());
        tvSeasonInfoMapper.updateById(series);
    }

    public void updateAnalysisStatusBatch(List<String> seasonGuids, AnalysisStatus status) {
        if (seasonGuids == null || seasonGuids.isEmpty()) {
            return;
        }

        // 批量查询已存在的记录
        List<TvSeasonInfo> existingList = tvSeasonInfoMapper.selectList(
                new LambdaQueryWrapper<TvSeasonInfo>().in(TvSeasonInfo::getSeasonGuid, seasonGuids)
        );
        Set<String> existingGuids = existingList.stream()
                .map(TvSeasonInfo::getSeasonGuid)
                .collect(Collectors.toSet());

        List<String> toUpdate = seasonGuids.stream()
                .filter(existingGuids::contains)
                .collect(Collectors.toList());
        List<String> toInsert = seasonGuids.stream()
                .filter(guid -> !existingGuids.contains(guid))
                .toList();

        LocalDateTime now = LocalDateTime.now();

        // 批量更新已存在的记录
        if (!toUpdate.isEmpty()) {
            LambdaUpdateWrapper<TvSeasonInfo> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.in(TvSeasonInfo::getSeasonGuid, toUpdate)
                    .set(TvSeasonInfo::getStatus, status)
                    .set(TvSeasonInfo::getUpdateTime, now);
            tvSeasonInfoMapper.update(null, updateWrapper);
        }

        // 批量插入不存在的记录
        if (!toInsert.isEmpty()) {
            for (String guid : toInsert) {
                TvSeasonInfo series = new TvSeasonInfo();
                series.setSeasonGuid(guid);
                series.setStatus(status);
                series.setCreateTime(now);
                series.setUpdateTime(now);
                tvSeasonInfoMapper.insert(series);
            }
        }
    }

    public EpisodeSegmentsResponse getSegmentsByEpisodeGuid(String episodeGuid) {
        EpisodeSegment segment = findEpisodeSegmentByGuid(episodeGuid);

        if (segment == null) {
            return new EpisodeSegmentsResponse();
        }

        EpisodeSegmentsResponse response = new EpisodeSegmentsResponse();
        response.setIntro(toSegmentDTO(segment.getIntroStart(), segment.getIntroEnd()));
        response.setCredits(toSegmentDTO(segment.getCreditsStart(), segment.getCreditsEnd()));
        response.setRecap(toSegmentDTO(segment.getRecapStart(), segment.getRecapEnd()));
        response.setPreview(toSegmentDTO(segment.getPreviewStart(), segment.getPreviewEnd()));
        response.setCommercial(toSegmentDTO(segment.getCommercialStart(), segment.getCommercialEnd()));
        return response;
    }

    private SegmentDTO toSegmentDTO(BigDecimal start, BigDecimal end) {
        if (start == null || end == null) {
            return null;
        }
        return new SegmentDTO(start, end, true);
    }

    private void analyzeSeasonInternal(String seasonGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes,
                                       String tvTitle, Integer seasonNumber, String userGuid) {
        log.info("Starting analysis for series {} in folder {} (user: {})", seasonGuid, seasonFolderPath, userGuid);
        updateAnalysisStatus(seasonGuid, AnalysisStatus.IN_PROGRESS);

        try {
            upsertSeries(seasonGuid, seasonFolderPath, tvTitle, seasonNumber, AnalysisStatus.IN_PROGRESS);

            SmartSkipConfig config = loadConfig(userGuid);
            log.info("Smart skip config for user {}: {}", userGuid, config);

            List<QueuedEpisode> queue = buildQueue(seasonGuid, seasonFolderPath, episodes);
            if (queue.isEmpty()) {
                log.info("No episodes found in {}", seasonFolderPath);
                updateAnalysisStatus(seasonGuid, AnalysisStatus.COMPLETED);
                return;
            }
            log.info("Found {} episodes", queue.size());

            upsertEpisodeSegmentsFromQueue(seasonGuid, queue, AnalysisStatus.IN_PROGRESS);
            hydrateQueueFromExistingSegments(seasonGuid, queue);
            prepareEpisodesForAnalysis(queue, config);
            runDefaultAnalysis(queue, config);

            PersistSummary summary = persistResults(seasonGuid, queue);
            updateAnalysisStatus(seasonGuid, resolveSeasonStatus(summary));
        } catch (Exception e) {
            log.error("Error during analysis for series {}", seasonGuid, e);
            updateAnalysisStatus(seasonGuid, AnalysisStatus.FAILED);
            throw e;
        }
    }

    private List<QueuedEpisode> buildQueue(String seasonGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes) {
        return mediaFileScanner.getEpisodeQueue(seasonGuid, seasonFolderPath, episodes);
    }

    private void prepareEpisodesForAnalysis(List<QueuedEpisode> queue, SmartSkipConfig config) {
        for (QueuedEpisode ep : queue) {
            ep.setIntroFingerprintEnd(config.getIntroFingerprintEnd(ep.getDuration()));
            ep.setCreditsFingerprintStart(config.getCreditsFingerprintStart(ep.getDuration()));
            for (AnalysisMode mode : AnalysisMode.values()) {
                if (!isModeEnabled(mode, config)) {
                    continue; // keep previously hydrated results for disabled modes
                }
                ep.setAnalyzed(mode, false);
                ep.setSegment(mode, null);
            }
            ep.setAnalysisFailed(false);
        }
    }

    private boolean isModeEnabled(AnalysisMode mode, SmartSkipConfig config) {
        switch (mode) {
            case INTRODUCTION: return config.isScanIntroduction();
            case CREDITS: return config.isScanCredits();
            case RECAP: return config.isScanRecap();
            case PREVIEW: return config.isScanPreview();
            case COMMERCIAL: return config.isScanCommercial();
            default: return false;
        }
    }

    private void runDefaultAnalysis(List<QueuedEpisode> queue, SmartSkipConfig config) {
        boolean isAnime = config.isAnimeDetection();
        boolean isMovie = !queue.isEmpty() && queue.stream().allMatch(QueuedEpisode::isMovie);
        AnalyzerAction action = AnalyzerAction.DEFAULT;

        for (AnalysisMode mode : AnalysisMode.values()) {
            if (!isModeEnabled(mode, config)) {
                log.info("Skipping disabled analysis mode {}", mode);
                continue;
            }
            List<MediaFileAnalyzer> analyzers = analyzerFactory.createAnalyzers(mode, isAnime, isMovie, action, config);
            runAnalyzers(analyzers, queue, mode);
        }
    }

    private void runAnalyzers(List<MediaFileAnalyzer> analyzers, List<QueuedEpisode> queue, AnalysisMode mode) {
        for (MediaFileAnalyzer analyzer : analyzers) {
            try {
                analyzer.analyze(queue, mode);
            } catch (Exception e) {
                log.error("Error running analyzer " + analyzer.getClass().getSimpleName(), e);
            }
        }
    }

    private PersistSummary persistResults(String seasonGuid, List<QueuedEpisode> queue) {
        int failedCount = 0;
        LocalDateTime now = LocalDateTime.now();

        List<EpisodeSegment> existingSegments = episodeSegmentMapper.selectList(
            new QueryWrapper<EpisodeSegment>().eq("season_guid", seasonGuid)
        );
        Map<Integer, EpisodeSegment> segmentMap = existingSegments.stream()
            .collect(Collectors.toMap(EpisodeSegment::getEpisodeNumber, s -> s, (a, b) -> a));

        for (QueuedEpisode ep : queue) {
            EpisodeSegment existing = segmentMap.get(ep.getEpisodeNumber());
            if (persistEpisodeResult(seasonGuid, ep, now, existing)) {
                failedCount++;
            }
        }
        return new PersistSummary(failedCount, queue.size());
    }

    private AnalysisStatus resolveSeasonStatus(PersistSummary summary) {
        if (summary.total == 0) {
            return AnalysisStatus.COMPLETED;
        }
        if (summary.failedCount == summary.total) {
            return AnalysisStatus.FAILED;
        }
        if (summary.failedCount > 0) {
            return AnalysisStatus.PARTIAL_SUCCESS;
        }
        return AnalysisStatus.COMPLETED;
    }

    private boolean persistEpisodeResult(String seasonGuid, QueuedEpisode ep, LocalDateTime now, EpisodeSegment segment) {
        try {
            // A failure (bad probe or analyzer exception) must not be cached as
            // "no segments" — mark it retryable instead.
            boolean failed = ep.getDuration() <= 0 || ep.isAnalysisFailed();

            boolean isNew = (segment == null);
            if (isNew) {
                segment = new EpisodeSegment();
                segment.setSeasonGuid(seasonGuid);
                segment.setEpisodeNumber(ep.getEpisodeNumber());
            }

            analysisEntityMapper.updateEpisodeFromQueuedEpisode(segment, ep);

            applySegment(segment, AnalysisMode.INTRODUCTION, ep.getIntroSegment());
            applySegment(segment, AnalysisMode.CREDITS, ep.getCreditsSegment());
            applySegment(segment, AnalysisMode.RECAP, sanitizeRecapSegment(ep.getRecapSegment(), ep.getIntroSegment()));
            applySegment(segment, AnalysisMode.PREVIEW, ep.getPreviewSegment());
            applySegment(segment, AnalysisMode.COMMERCIAL, ep.getCommercialSegment());

            segment.setAction(buildActions(ep));
            segment.setStatus(failed ? AnalysisStatus.FAILED : AnalysisStatus.COMPLETED);

            saveOrUpdateEpisodeSegment(segment, now, isNew);

            return failed;
        } catch (Exception e) {
            log.error("Failed to persist episode result for episode {}", ep.getEpisodeNumber(), e);
            updateEpisodeStatus(seasonGuid, ep.getEpisodeNumber(), AnalysisStatus.FAILED, ep.getEpisodeGuid(), ep.getPath());
            return true;
        }
    }

    /**
     * Last-line-of-defense invariant from upstream: a recap that mirrors the
     * introduction (or would run past its start) is an analysis artifact, not a
     * real previously-on segment — drop it.
     */
    private Segment sanitizeRecapSegment(Segment recap, Segment intro) {
        if (recap == null || !recap.isValid()) {
            return null;
        }
        if (intro == null || !intro.isValid()) {
            return recap;
        }
        if (recap.getEnd() > intro.getStart()) {
            log.info("Dropping recap {}-{} that overlaps intro starting at {}", recap.getStart(), recap.getEnd(), intro.getStart());
            return null;
        }
        return recap;
    }

    private void applySegment(EpisodeSegment segment, AnalysisMode mode, Segment value) {
        BigDecimal start = value != null ? BigDecimal.valueOf(value.getStart()) : null;
        BigDecimal end = value != null ? BigDecimal.valueOf(value.getEnd()) : null;
        switch (mode) {
            case INTRODUCTION: segment.setIntroStart(start); segment.setIntroEnd(end); break;
            case CREDITS: segment.setCreditsStart(start); segment.setCreditsEnd(end); break;
            case RECAP: segment.setRecapStart(start); segment.setRecapEnd(end); break;
            case PREVIEW: segment.setPreviewStart(start); segment.setPreviewEnd(end); break;
            case COMMERCIAL: segment.setCommercialStart(start); segment.setCommercialEnd(end); break;
        }
    }

    private void saveOrUpdateEpisodeSegment(EpisodeSegment segment, LocalDateTime now, boolean isNew) {
        if (isNew) {
            segment.setCreateTime(now);
            segment.setUpdateTime(now);
            episodeSegmentMapper.insert(segment);
        } else {
            if (segment.getCreateTime() == null) {
                segment.setCreateTime(now);
            }
            segment.setUpdateTime(now);
            episodeSegmentMapper.updateById(segment);
        }
    }

    private record PersistSummary(int failedCount, int total) {
    }

    private void updateEpisodeStatus(String seasonGuid, int episodeNumber, AnalysisStatus status, String guid, String filePath) {
        EpisodeSegment segment = findEpisodeSegmentBySeriesAndNumber(seasonGuid, episodeNumber);
        LocalDateTime now = LocalDateTime.now();
        boolean isNew = (segment == null);
        if (isNew) {
            segment = new EpisodeSegment();
            segment.setSeasonGuid(seasonGuid);
            segment.setEpisodeNumber(episodeNumber);
        }
        segment.setGuid(guid);
        segment.setFilePath(filePath);
        segment.setStatus(status);
        saveOrUpdateEpisodeSegment(segment, now, isNew);
    }

    private void hydrateQueueFromExistingSegments(String seasonGuid, List<QueuedEpisode> queue) {
        List<EpisodeSegment> existingSegments = episodeSegmentMapper.selectList(
            new QueryWrapper<EpisodeSegment>().eq("season_guid", seasonGuid)
        );
        Map<Integer, EpisodeSegment> segmentMap = existingSegments.stream()
            .collect(Collectors.toMap(EpisodeSegment::getEpisodeNumber, s -> s, (a, b) -> a));

        for (QueuedEpisode ep : queue) {
            EpisodeSegment existing = segmentMap.get(ep.getEpisodeNumber());
            if (existing != null) {
                ep.setIntroFingerprint(existing.getIntroFingerprint());
                ep.setCreditsFingerprint(existing.getCreditsFingerprint());
                ep.setRecapFingerprint(existing.getRecapFingerprint());
                if (existing.getDuration() != null) {
                    ep.setDuration(existing.getDuration());
                }

                Segment intro = toSegment(existing.getIntroStart(), existing.getIntroEnd());
                if (intro != null) ep.setIntroSegment(intro);
                Segment credits = toSegment(existing.getCreditsStart(), existing.getCreditsEnd());
                if (credits != null) ep.setCreditsSegment(credits);
                Segment recap = toSegment(existing.getRecapStart(), existing.getRecapEnd());
                if (recap != null) ep.setRecapSegment(recap);
                Segment preview = toSegment(existing.getPreviewStart(), existing.getPreviewEnd());
                if (preview != null) ep.setPreviewSegment(preview);
                Segment commercial = toSegment(existing.getCommercialStart(), existing.getCommercialEnd());
                if (commercial != null) ep.setCommercialSegment(commercial);

                parseActions(existing.getAction(), ep);
            }

            ensureDuration(ep);
        }
    }

    private Segment toSegment(BigDecimal start, BigDecimal end) {
        if (start == null || end == null) {
            return null;
        }
        return new Segment(start.doubleValue(), end.doubleValue(), true);
    }

    private void ensureDuration(QueuedEpisode ep) {
        if (ep.getDuration() > 0) {
            return;
        }
        try {
            ep.setDuration(ffmpegWrapper.getDuration(ep.getPath()));
        } catch (Exception e) {
            log.error("Failed to get duration for " + ep.getPath(), e);
        }
    }

    private void upsertSeries(String seasonGuid, String seasonFolderPath, String tvTitle, Integer seasonNumber, AnalysisStatus status) {
        TvSeasonInfo series = tvSeasonInfoMapper.selectById(seasonGuid);
        LocalDateTime now = LocalDateTime.now();
        boolean isNew = (series == null);
        if (isNew) {
            series = new TvSeasonInfo();
            series.setSeasonGuid(seasonGuid);
            series.setCreateTime(now);
        }

        analysisEntityMapper.updateTvSeasonInfo(series, seasonFolderPath, tvTitle, seasonNumber);
        series.setStatus(status);
        if (series.getCreateTime() == null) {
            series.setCreateTime(now);
        }
        series.setUpdateTime(now);

        if (isNew) {
            tvSeasonInfoMapper.insert(series);
        } else {
            tvSeasonInfoMapper.updateById(series);
        }
    }

    private void upsertEpisodeSegmentsFromRequest(String seasonGuid, List<EpisodeDetailRequest> episodes, AnalysisStatus status) {
        LocalDateTime now = LocalDateTime.now();
        List<EpisodeSegment> existingSegments = episodeSegmentMapper.selectList(
            new QueryWrapper<EpisodeSegment>().eq("season_guid", seasonGuid)
        );
        Map<Integer, EpisodeSegment> segmentMap = existingSegments.stream()
            .collect(Collectors.toMap(EpisodeSegment::getEpisodeNumber, s -> s, (a, b) -> a));

        for (EpisodeDetailRequest ep : episodes) {
            if (ep == null || ep.getEpisodeNumber() == null) {
                continue;
            }
            EpisodeSegment segment = segmentMap.get(ep.getEpisodeNumber());
            boolean isNew = segment == null;
            if (isNew) {
                segment = new EpisodeSegment();
                segment.setSeasonGuid(seasonGuid);
                segment.setEpisodeNumber(ep.getEpisodeNumber());
            }

            analysisEntityMapper.updateEpisodeFromRequest(segment, ep);
            segment.setStatus(status);
            saveOrUpdateEpisodeSegment(segment, now, isNew);
        }
    }

    private void upsertEpisodeSegmentsFromQueue(String seasonGuid, List<QueuedEpisode> queue, AnalysisStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            LocalDateTime now = LocalDateTime.now();
            List<EpisodeSegment> existingSegments = episodeSegmentMapper.selectList(
                new QueryWrapper<EpisodeSegment>().eq("season_guid", seasonGuid)
            );
            Map<Integer, EpisodeSegment> segmentMap = existingSegments.stream()
                .collect(Collectors.toMap(EpisodeSegment::getEpisodeNumber, s -> s, (a, b) -> a));

            for (QueuedEpisode ep : queue) {
                EpisodeSegment segment = segmentMap.get(ep.getEpisodeNumber());
                boolean isNew = segment == null;
                if (isNew) {
                    segment = new EpisodeSegment();
                    segment.setSeasonGuid(seasonGuid);
                    segment.setEpisodeNumber(ep.getEpisodeNumber());
                }

                analysisEntityMapper.updateEpisodeFromQueuedEpisode(segment, ep);
                segment.setStatus(status);
                saveOrUpdateEpisodeSegment(segment, now, isNew);
            }
        });
    }

    private EpisodeSegment findEpisodeSegmentBySeriesAndNumber(String seasonGuid, int episodeNumber) {
        return episodeSegmentMapper.selectOne(
            new QueryWrapper<EpisodeSegment>()
                .eq("season_guid", seasonGuid)
                .eq("episode_number", episodeNumber)
                .last("LIMIT 1")
        );
    }

    private EpisodeSegment findEpisodeSegmentByGuid(String episodeGuid) {
        return episodeSegmentMapper.selectOne(
            new QueryWrapper<EpisodeSegment>()
                .eq("guid", episodeGuid)
                .last("LIMIT 1")
        );
    }

    private String buildActions(QueuedEpisode ep) {
        StringBuilder sb = new StringBuilder();
        for (AnalysisMode mode : AnalysisMode.values()) {
            AnalyzerAction action = ep.getAnalyzerAction(mode);
            if (action == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(mode.segmentKey()).append('=').append(action.name());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private void parseActions(String action, QueuedEpisode ep) {
        if (action == null || action.isBlank()) {
            return;
        }

        String trimmed = action.trim();
        if (!trimmed.contains("=")) {
            // Legacy single-value format applies to Introduction and Credits
            try {
                AnalyzerAction a = AnalyzerAction.valueOf(trimmed);
                ep.setIntroAction(a);
                ep.setCreditsAction(a);
            } catch (Exception ignored) {
            }
            return;
        }

        String[] parts = trimmed.split(";");
        for (String part : parts) {
            String p = part.trim();
            int idx = p.indexOf('=');
            if (idx <= 0 || idx >= p.length() - 1) {
                continue;
            }
            String key = p.substring(0, idx).trim();
            String value = p.substring(idx + 1).trim();
            try {
                AnalyzerAction a = AnalyzerAction.valueOf(value);
                for (AnalysisMode mode : AnalysisMode.values()) {
                    if (mode.segmentKey().equalsIgnoreCase(key)) {
                        ep.setAnalyzerAction(mode, a);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private record AnalyzeJob(String seasonGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes,
                              LocalDateTime enqueuedAt, String tvTitle, Integer seasonNumber, String userGuid) {
    }
}
