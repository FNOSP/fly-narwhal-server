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
import com.jankinwu.flynarwhal.web.mapper.DbVersionMapper;
import com.jankinwu.flynarwhal.web.mapper.EpisodeSegmentMapper;
import com.jankinwu.flynarwhal.web.mapper.TvSeasonInfoMapper;
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
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
@Import({
    com.jankinwu.flynarwhal.core.analyzer.ChapterAnalyzer.class,
    com.jankinwu.flynarwhal.core.analyzer.BlackFrameAnalyzer.class,
    com.jankinwu.flynarwhal.core.analyzer.BlackFrameAltAnalyzer.class,
    com.jankinwu.flynarwhal.core.analyzer.ChromaprintAnalyzer.class,
    AnalyzerFactory.class,
    MediaFileScanner.class
})
public class AnalysisService {

    private final TvSeasonInfoMapper tvSeasonInfoMapper;
    private final EpisodeSegmentMapper episodeSegmentMapper;
    private final DbVersionMapper dbVersionMapper;
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
                           AnalyzerFactory analyzerFactory,
                           MediaFileScanner mediaFileScanner,
                           TransactionTemplate transactionTemplate,
                           AnalysisEntityMapper analysisEntityMapper) {
        this.tvSeasonInfoMapper = tvSeasonInfoMapper;
        this.episodeSegmentMapper = episodeSegmentMapper;
        this.dbVersionMapper = dbVersionMapper;
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
                    analyzeSeasonInternal(job.seasonGuid, job.seasonFolderPath, job.episodes, job.tvTitle, job.seasonNumber);
                } catch (Exception e) {
                    log.error("Error analyzing season internal", e);
//                    status.setRollbackOnly();
                    updateAnalysisStatus(job.seasonGuid, AnalysisStatus.FAILED);
                }
//                transactionTemplate.executeWithoutResult(status -> {
//
//                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing analyze job", e);
            }
        }
    }

    public int enqueueAnalyzeSeason(String seasonGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes, String tvTitle, Integer seasonNumber) {
        List<EpisodeDetailRequest> safeEpisodes = episodes == null ? List.of() : List.copyOf(episodes);
        logPathEncoding("enqueueAnalyzeSeason.seasonFolderPath", seasonFolderPath);
        logEpisodePaths("enqueueAnalyzeSeason.requestEpisodes", safeEpisodes);
        registerPending(seasonGuid, seasonFolderPath, tvTitle, seasonNumber, safeEpisodes);
        enqueueJob(seasonGuid, seasonFolderPath, safeEpisodes, tvTitle, seasonNumber);
        return analyzeJobQueue.size();
    }

    private void enqueueJob(String seasonGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes, String tvTitle, Integer seasonNumber) {
        analyzeJobQueue.addLast(new AnalyzeJob(seasonGuid, seasonFolderPath, episodes, LocalDateTime.now(), tvTitle, seasonNumber));
    }

    private void registerPending(String seasonGuid, String seasonFolderPath, String tvTitle, Integer seasonNumber, List<EpisodeDetailRequest> episodes) {
        logPathEncoding("registerPending.seasonFolderPath", seasonFolderPath);
        logEpisodePaths("registerPending.requestEpisodes", episodes);
        transactionTemplate.executeWithoutResult(tx -> {
            upsertSeries(seasonGuid, seasonFolderPath, tvTitle, seasonNumber, AnalysisStatus.PENDING);
            upsertEpisodeSegmentsFromRequest(seasonGuid, episodes, AnalysisStatus.PENDING);
        });
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
        if (segment.getIntroStart() != null && segment.getIntroEnd() != null) {
            response.setIntro(new SegmentDTO(segment.getIntroStart(), segment.getIntroEnd(), true));
        }
        if (segment.getCreditsStart() != null && segment.getCreditsEnd() != null) {
            response.setCredits(new SegmentDTO(segment.getCreditsStart(), segment.getCreditsEnd(), true));
        }

        return response;
    }

    private void analyzeSeasonInternal(String seasonGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes, String tvTitle, Integer seasonNumber) {
        log.info("Starting analysis for series {} in folder {}", seasonGuid, seasonFolderPath);
        logPathEncoding("analyzeSeasonInternal.seasonFolderPath", seasonFolderPath);
        logEpisodePaths("analyzeSeasonInternal.requestEpisodes", episodes);
        updateAnalysisStatus(seasonGuid, AnalysisStatus.IN_PROGRESS);

        try {
            upsertSeries(seasonGuid, seasonFolderPath, tvTitle, seasonNumber, AnalysisStatus.IN_PROGRESS);

            List<QueuedEpisode> queue = buildQueue(seasonGuid, seasonFolderPath, episodes);
            logQueuedEpisodePaths("analyzeSeasonInternal.queue", queue);
            if (queue.isEmpty()) {
                log.info("No episodes found in {}", seasonFolderPath);
                updateAnalysisStatus(seasonGuid, AnalysisStatus.COMPLETED);
                return;
            }
            log.info("Found {} episodes", queue.size());

            upsertEpisodeSegmentsFromQueue(seasonGuid, queue, AnalysisStatus.IN_PROGRESS);
            hydrateQueueFromExistingSegments(seasonGuid, queue);
            prepareEpisodesForAnalysis(queue);
            runDefaultAnalysis(queue);

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

    private void prepareEpisodesForAnalysis(List<QueuedEpisode> queue) {
        for (QueuedEpisode ep : queue) {
            double duration = ep.getDuration();
            double introFingerprintEnd = duration > 0 ? Math.min(600, duration) : 600;
            double creditsFingerprintStart = duration > 0 ? Math.max(0, duration - 240) : 0;
            ep.setIntroFingerprintEnd(introFingerprintEnd);
            ep.setCreditsFingerprintStart(creditsFingerprintStart);
            ep.setIntroAnalyzed(false);
            ep.setCreditsAnalyzed(false);
            log.info("[AnalysisPrep] episodeNumber={} duration={} introFpEnd={} creditsFpStart={}",
                ep.getEpisodeNumber(), ep.getDuration(), ep.getIntroFingerprintEnd(), ep.getCreditsFingerprintStart());
        }
    }

    private void runDefaultAnalysis(List<QueuedEpisode> queue) {
        boolean isAnime = false;
        boolean isMovie = false;
        AnalyzerAction action = AnalyzerAction.DEFAULT;

        List<MediaFileAnalyzer> introAnalyzers = analyzerFactory.createAnalyzers(AnalysisMode.INTRODUCTION, isAnime, isMovie, action);
        log.info("[AnalysisRun] mode={} analyzers={}", AnalysisMode.INTRODUCTION,
            introAnalyzers.stream().map(a -> a.getClass().getSimpleName()).collect(Collectors.toList()));
        runAnalyzers(introAnalyzers, queue, AnalysisMode.INTRODUCTION);

        List<MediaFileAnalyzer> creditsAnalyzers = analyzerFactory.createAnalyzers(AnalysisMode.CREDITS, isAnime, isMovie, action);
        log.info("[AnalysisRun] mode={} analyzers={}", AnalysisMode.CREDITS,
            creditsAnalyzers.stream().map(a -> a.getClass().getSimpleName()).collect(Collectors.toList()));
        runAnalyzers(creditsAnalyzers, queue, AnalysisMode.CREDITS);

        for (QueuedEpisode ep : queue) {
            log.info("[AnalysisResult] episodeNumber={} duration={} introAnalyzed={} creditsAnalyzed={} introAction={} creditsAction={} intro={} credits={}",
                ep.getEpisodeNumber(),
                ep.getDuration(),
                ep.isIntroAnalyzed(),
                ep.isCreditsAnalyzed(),
                ep.getIntroAction(),
                ep.getCreditsAction(),
                segmentToString(ep.getIntroSegment()),
                segmentToString(ep.getCreditsSegment()));
        }
    }

    private void runAnalyzers(List<MediaFileAnalyzer> analyzers, List<QueuedEpisode> queue, AnalysisMode mode) {
        for (MediaFileAnalyzer analyzer : analyzers) {
            try {
                int before = countAnalyzed(queue, mode);
                log.info("[AnalyzerRun] mode={} analyzer={} beforeAnalyzed={}/{}", mode, analyzer.getClass().getSimpleName(), before, queue.size());
                analyzer.analyze(queue, mode);
                int after = countAnalyzed(queue, mode);
                log.info("[AnalyzerRun] mode={} analyzer={} afterAnalyzed={}/{}", mode, analyzer.getClass().getSimpleName(), after, queue.size());
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
            boolean failed = ep.getDuration() <= 0;

            boolean isNew = (segment == null);
            if (isNew) {
                segment = new EpisodeSegment();
                segment.setSeasonGuid(seasonGuid);
                segment.setEpisodeNumber(ep.getEpisodeNumber());
            }

            analysisEntityMapper.updateEpisodeFromQueuedEpisode(segment, ep);

            if (ep.getIntroSegment() != null) {
                segment.setIntroStart(BigDecimal.valueOf(ep.getIntroSegment().getStart()));
                segment.setIntroEnd(BigDecimal.valueOf(ep.getIntroSegment().getEnd()));
            } else {
                segment.setIntroStart(null);
                segment.setIntroEnd(null);
            }

            if (ep.getCreditsSegment() != null) {
                segment.setCreditsStart(BigDecimal.valueOf(ep.getCreditsSegment().getStart()));
                segment.setCreditsEnd(BigDecimal.valueOf(ep.getCreditsSegment().getEnd()));
            } else {
                segment.setCreditsStart(null);
                segment.setCreditsEnd(null);
            }

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
                if (existing.getDuration() != null) {
                    ep.setDuration(existing.getDuration());
                }

                if (existing.getIntroStart() != null && existing.getIntroEnd() != null) {
                    ep.setIntroSegment(new Segment(existing.getIntroStart().doubleValue(), existing.getIntroEnd().doubleValue(), true));
                }
                if (existing.getCreditsStart() != null && existing.getCreditsEnd() != null) {
                    ep.setCreditsSegment(new Segment(existing.getCreditsStart().doubleValue(), existing.getCreditsEnd().doubleValue(), true));
                }

                parseActions(existing.getAction(), ep);
            }

            ensureDuration(ep);
        }
    }

    private void ensureDuration(QueuedEpisode ep) {
        if (ep.getDuration() > 0) {
            return;
        }
        try {
            logPathEncoding("ensureDuration.episodePath", ep.getPath());
            log.info("[EnsureDuration] episodeNumber={} currentDuration={}", ep.getEpisodeNumber(), ep.getDuration());
            ep.setDuration(ffmpegWrapper.getDuration(ep.getPath()));
            log.info("[EnsureDuration] episodeNumber={} updatedDuration={}", ep.getEpisodeNumber(), ep.getDuration());
            if (ep.getDuration() <= 0) {
                log.warn("[EnsureDuration] episodeNumber={} durationStillZero path={}", ep.getEpisodeNumber(), ep.getPath());
            }
        } catch (Exception e) {
            log.error("Failed to get duration for " + ep.getPath(), e);
        }
    }

    private int countAnalyzed(List<QueuedEpisode> queue, AnalysisMode mode) {
        int count = 0;
        for (QueuedEpisode ep : queue) {
            if (ep == null) {
                continue;
            }
            if (mode == AnalysisMode.INTRODUCTION) {
                if (ep.isIntroAnalyzed()) {
                    count++;
                }
            } else if (mode == AnalysisMode.CREDITS) {
                if (ep.isCreditsAnalyzed()) {
                    count++;
                }
            }
        }
        return count;
    }

    private String segmentToString(Segment segment) {
        if (segment == null) {
            return "<null>";
        }
        return "start=" + segment.getStart() + ",end=" + segment.getEnd() + ",duration=" + segment.getDuration() + ",valid=" + segment.isValid();
    }

    private void logEpisodePaths(String stage, List<EpisodeDetailRequest> episodes) {
        if (episodes == null || episodes.isEmpty()) {
            return;
        }
        for (EpisodeDetailRequest ep : episodes) {
            if (ep == null) {
                continue;
            }
            logPathEncoding(stage + ".filePath", ep.getFilePath());
        }
    }

    private void logQueuedEpisodePaths(String stage, List<QueuedEpisode> episodes) {
        if (episodes == null || episodes.isEmpty()) {
            return;
        }
        for (QueuedEpisode ep : episodes) {
            if (ep == null) {
                continue;
            }
            logPathEncoding(stage + ".filePath", ep.getPath());
        }
    }

    private void logPathEncoding(String stage, String path) {
        if (path == null) {
            log.info("[PathEncoding] stage={} value=<null>", stage);
            return;
        }
        String trimmed = path.length() > 200 ? path.substring(0, 200) + "..." : path;
        boolean hasReplacement = path.indexOf('\uFFFD') >= 0;
        boolean roundTripUtf8 = path.equals(new String(path.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        byte[] utf8Bytes = path.getBytes(StandardCharsets.UTF_8);
        String hex = HexFormat.of().formatHex(utf8Bytes, 0, Math.min(64, utf8Bytes.length));
        log.info("[PathEncoding] stage={} len={} roundTripUtf8={} hasReplacement={} sample={} utf8HexPrefix={}",
                stage, path.length(), roundTripUtf8, hasReplacement, trimmed, hex);
        logPathFileState(stage, path);
    }

    private void logPathFileState(String stage, String path) {
        try {
            Path nioPath = Path.of(path);
            boolean exists = Files.exists(nioPath);
            boolean isFile = exists && Files.isRegularFile(nioPath);
            boolean readable = exists && Files.isReadable(nioPath);
            Path parent = nioPath.getParent();
            boolean parentExists = parent != null && Files.exists(parent);
            log.info("[PathFileState] stage={} exists={} isFile={} readable={} parentExists={}", stage, exists, isFile, readable, parentExists);
        } catch (Exception e) {
            log.info("[PathFileState] stage={} error={}", stage, e.getClass().getSimpleName());
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
        AnalyzerAction intro = ep.getIntroAction();
        AnalyzerAction credits = ep.getCreditsAction();
        if (intro == null && credits == null) {
            return null;
        }
        if (intro != null && credits != null && intro == credits) {
            return intro.name();
        }
        if (intro != null && credits != null) {
            return "INTRODUCTION=" + intro.name() + ";CREDITS=" + credits.name();
        }
        if (intro != null) {
            return "INTRODUCTION=" + intro.name();
        }
        return "CREDITS=" + credits.name();
    }

    private void parseActions(String action, QueuedEpisode ep) {
        if (action == null || action.isBlank()) {
            return;
        }

        String trimmed = action.trim();
        if (!trimmed.contains("=")) {
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
                if ("INTRODUCTION".equalsIgnoreCase(key)) {
                    ep.setIntroAction(a);
                } else if ("CREDITS".equalsIgnoreCase(key)) {
                    ep.setCreditsAction(a);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private record AnalyzeJob(String seasonGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes, LocalDateTime enqueuedAt, String tvTitle, Integer seasonNumber) {
    }
}
