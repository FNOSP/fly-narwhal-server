package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jankinwu.flynarwhal.core.analyzer.AnalyzerFactory;
import com.jankinwu.flynarwhal.core.analyzer.MediaFileAnalyzer;
import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.AnalyzerAction;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import com.jankinwu.flynarwhal.core.scanner.MediaFileScanner;
import com.jankinwu.flynarwhal.core.dto.response.EpisodeSegmentsResponse;
import com.jankinwu.flynarwhal.web.entity.EpisodeSegment;
import com.jankinwu.flynarwhal.web.entity.SeriesEpisode;
import com.jankinwu.flynarwhal.core.dto.request.EpisodeDetailRequest;
import com.jankinwu.flynarwhal.web.mapper.EpisodeSegmentMapper;
import com.jankinwu.flynarwhal.web.mapper.SeriesEpisodeMapper;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

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

    private final SeriesEpisodeMapper seriesEpisodeMapper;
    private final EpisodeSegmentMapper episodeSegmentMapper;
    private final AnalyzerFactory analyzerFactory;
    private final MediaFileScanner mediaFileScanner;
    private final FFmpegWrapper ffmpegWrapper;
    private final TransactionTemplate transactionTemplate;
    private final BlockingDeque<AnalyzeJob> analyzeJobQueue = new LinkedBlockingDeque<>();

    public AnalysisService(SeriesEpisodeMapper seriesEpisodeMapper,
                           EpisodeSegmentMapper episodeSegmentMapper,
                           AnalyzerFactory analyzerFactory,
                           MediaFileScanner mediaFileScanner,
                           TransactionTemplate transactionTemplate) {
        this.seriesEpisodeMapper = seriesEpisodeMapper;
        this.episodeSegmentMapper = episodeSegmentMapper;
        this.analyzerFactory = analyzerFactory;
        this.mediaFileScanner = mediaFileScanner;
        this.ffmpegWrapper = new FFmpegWrapper();
        this.transactionTemplate = transactionTemplate;
    }

    @PostConstruct
    public void startWorker() {
        Thread worker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    AnalyzeJob job = analyzeJobQueue.takeFirst();
                    transactionTemplate.executeWithoutResult(status -> {
                        try {
                            analyzeSeasonInternal(job.seriesGuid, job.seasonFolderPath, job.episodes);
                        } catch (Exception e) {
                            status.setRollbackOnly();
                            log.error("Error processing analyze job for seriesGuid={}", job.seriesGuid, e);
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.error("Error in analyze worker loop", e);
                }
            }
        }, "analysis-job-worker");
        worker.setDaemon(true);
        worker.start();
    }

    public int enqueueAnalyzeSeason(String seriesGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes) {
        List<EpisodeDetailRequest> safeEpisodes = episodes == null ? List.of() : List.copyOf(episodes);
        analyzeJobQueue.addLast(new AnalyzeJob(seriesGuid, seasonFolderPath, safeEpisodes, LocalDateTime.now()));
        return analyzeJobQueue.size();
    }

    public EpisodeSegmentsResponse getSegmentsByEpisodeGuid(String episodeGuid) {
        EpisodeSegment segment = episodeSegmentMapper.selectOne(
            new QueryWrapper<EpisodeSegment>()
                .eq("guid", episodeGuid)
                .last("LIMIT 1")
        );

        if (segment == null) {
            return new EpisodeSegmentsResponse();
        }

        EpisodeSegmentsResponse response = new EpisodeSegmentsResponse();
        if (segment.getIntroStart() != null && segment.getIntroEnd() != null) {
            response.setIntro(new Segment(segment.getIntroStart(), segment.getIntroEnd(), true));
        }
        if (segment.getCreditsStart() != null && segment.getCreditsEnd() != null) {
            response.setCredits(new Segment(segment.getCreditsStart(), segment.getCreditsEnd(), true));
        }

        return response;
    }

    private void analyzeSeasonInternal(String seriesGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes) {
        log.info("Starting analysis for series {} in folder {}", seriesGuid, seasonFolderPath);

        // 1. Check/Register Series
        SeriesEpisode series = seriesEpisodeMapper.selectById(seriesGuid);
        if (series == null) {
            series = new SeriesEpisode();
            series.setSeriesGuid(seriesGuid);
            series.setSeasonFolderPath(seasonFolderPath);
            seriesEpisodeMapper.insert(series);
        } else {
            // Update path if changed
            if (!seasonFolderPath.equals(series.getSeasonFolderPath())) {
                series.setSeasonFolderPath(seasonFolderPath);
                seriesEpisodeMapper.updateById(series);
            }
        }

        // 2. Scan for episodes
        List<QueuedEpisode> queue = mediaFileScanner.getEpisodeQueue(seriesGuid, seasonFolderPath, episodes);
        if (queue.isEmpty()) {
            log.info("No episodes found in {}", seasonFolderPath);
            return;
        }
        log.info("Found {} episodes", queue.size());

        // 3. Load existing state
        for (QueuedEpisode ep : queue) {
            // Fetch existing segment from DB
            EpisodeSegment existing = episodeSegmentMapper.selectOne(
                new QueryWrapper<EpisodeSegment>()
                    .eq("series_guid", seriesGuid)
                    .eq("episode_index", ep.getEpisodeIndex())
                    .last("LIMIT 1")
            );
            
            if (existing != null) {
                ep.setIntroFingerprint(existing.getIntroFingerprint());
                ep.setCreditsFingerprint(existing.getCreditsFingerprint());
                if (existing.getDuration() != null) {
                    ep.setDuration(existing.getDuration());
                }
                
                if (existing.getIntroStart() != null && existing.getIntroEnd() != null) {
                    ep.setIntroSegment(new Segment(existing.getIntroStart(), existing.getIntroEnd(), true));
                }
                if (existing.getCreditsStart() != null && existing.getCreditsEnd() != null) {
                    ep.setCreditsSegment(new Segment(existing.getCreditsStart(), existing.getCreditsEnd(), true));
                }

                parseActions(existing.getAction(), ep);
            } else {
                try {
                    ep.setDuration(ffmpegWrapper.getDuration(ep.getPath()));
                } catch (Exception e) {
                    log.error("Failed to get duration for " + ep.getPath(), e);
                }
            }

            if (ep.getDuration() <= 0) {
                try {
                    ep.setDuration(ffmpegWrapper.getDuration(ep.getPath()));
                } catch (Exception e) {
                    log.error("Failed to get duration for " + ep.getPath(), e);
                }
            }

            ep.setIntroFingerprintEnd(600);
            ep.setCreditsFingerprintStart(Math.max(0, ep.getDuration() - 240));

            ep.setIntroAnalyzed(false);
            ep.setCreditsAnalyzed(false);
        }

        // 4. Run Analysis Chain
        // Hardcoded flags for now, can be passed as params
        boolean isAnime = false; 
        boolean isMovie = false;
        AnalyzerAction action = AnalyzerAction.DEFAULT;

        // Analyze Introduction
        List<MediaFileAnalyzer> introAnalyzers = analyzerFactory.createAnalyzers(AnalysisMode.INTRODUCTION, isAnime, isMovie, action);
        runAnalyzers(introAnalyzers, queue, AnalysisMode.INTRODUCTION);

        // Analyze Credits
        List<MediaFileAnalyzer> creditsAnalyzers = analyzerFactory.createAnalyzers(AnalysisMode.CREDITS, isAnime, isMovie, action);
        runAnalyzers(creditsAnalyzers, queue, AnalysisMode.CREDITS);

        // 5. Save Results
        saveResults(queue, seriesGuid);
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

    private void saveResults(List<QueuedEpisode> queue, String seriesGuid) {
        for (QueuedEpisode ep : queue) {
            // Check if exists again to decide insert/update
            EpisodeSegment segment = episodeSegmentMapper.selectOne(
                new QueryWrapper<EpisodeSegment>()
                    .eq("series_guid", seriesGuid)
                    .eq("episode_index", ep.getEpisodeIndex())
                    .last("LIMIT 1")
            );

            boolean isNew = (segment == null);
            if (isNew) {
                segment = new EpisodeSegment();
                segment.setSeriesGuid(seriesGuid);
            }

            segment.setSeriesGuid(seriesGuid);
            segment.setGuid(ep.getEpisodeGuid());
            segment.setEpisodeIndex(ep.getEpisodeIndex());
            segment.setFilePath(ep.getPath());
            segment.setDuration(ep.getDuration());
            
            segment.setIntroStart(ep.getIntroSegment() == null ? null : ep.getIntroSegment().getStart());
            segment.setIntroEnd(ep.getIntroSegment() == null ? null : ep.getIntroSegment().getEnd());
            segment.setCreditsStart(ep.getCreditsSegment() == null ? null : ep.getCreditsSegment().getStart());
            segment.setCreditsEnd(ep.getCreditsSegment() == null ? null : ep.getCreditsSegment().getEnd());
            
            segment.setIntroFingerprint(ep.getIntroFingerprint());
            segment.setCreditsFingerprint(ep.getCreditsFingerprint());
            segment.setAction(buildActions(ep));

            LocalDateTime now = LocalDateTime.now();
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

    private record AnalyzeJob(String seriesGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes, LocalDateTime enqueuedAt) {
    }
}
