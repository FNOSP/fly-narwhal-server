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
import com.jankinwu.flynarwhal.web.entity.EpisodeSegment;
import com.jankinwu.flynarwhal.web.entity.SeriesEpisode;
import com.jankinwu.flynarwhal.core.dto.request.EpisodeDetailRequest;
import com.jankinwu.flynarwhal.web.mapper.EpisodeSegmentMapper;
import com.jankinwu.flynarwhal.web.mapper.SeriesEpisodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public AnalysisService(SeriesEpisodeMapper seriesEpisodeMapper,
                           EpisodeSegmentMapper episodeSegmentMapper,
                           AnalyzerFactory analyzerFactory,
                           MediaFileScanner mediaFileScanner) {
        this.seriesEpisodeMapper = seriesEpisodeMapper;
        this.episodeSegmentMapper = episodeSegmentMapper;
        this.analyzerFactory = analyzerFactory;
        this.mediaFileScanner = mediaFileScanner;
        this.ffmpegWrapper = new FFmpegWrapper();
    }

    @Transactional
    public void analyzeSeason(String seriesGuid, String seasonFolderPath, List<EpisodeDetailRequest> episodes) {
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
                // Restore state
                ep.setIntroFingerprint(existing.getIntroFingerprint());
                ep.setCreditsFingerprint(existing.getCreditsFingerprint());
                ep.setDuration(existing.getDuration());
                
                if (existing.getIntroStart() != null && existing.getIntroEnd() != null) {
                    ep.setIntroSegment(new Segment(existing.getIntroStart(), existing.getIntroEnd(), true));
                    ep.setIntroAnalyzed(true);
                }
                if (existing.getCreditsStart() != null && existing.getCreditsEnd() != null) {
                    ep.setCreditsSegment(new Segment(existing.getCreditsStart(), existing.getCreditsEnd(), true));
                    ep.setCreditsAnalyzed(true);
                }
            } else {
                // Get duration if new
                try {
                    ep.setDuration(ffmpegWrapper.getDuration(ep.getPath()));
                    // Config defaults
                    ep.setIntroFingerprintEnd(600); 
                    ep.setCreditsFingerprintStart(Math.max(0, ep.getDuration() - 240));
                } catch (Exception e) {
                    log.error("Failed to get duration for " + ep.getPath(), e);
                }
            }
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
            // Filter out already analyzed episodes for optimization?
            // But some batch analyzers (Chromaprint) might need all episodes context.
            // So we pass the full list, and let the analyzer decide (they check isAnalyzed).
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
                segment.setGuid(ep.getEpisodeGuid());
                segment.setEpisodeIndex(ep.getEpisodeIndex());
                segment.setFilePath(ep.getPath());
            }

            segment.setDuration(ep.getDuration());
            
            if (ep.getIntroSegment() != null) {
                segment.setIntroStart(ep.getIntroSegment().getStart());
                segment.setIntroEnd(ep.getIntroSegment().getEnd());
            }
            if (ep.getCreditsSegment() != null) {
                segment.setCreditsStart(ep.getCreditsSegment().getStart());
                segment.setCreditsEnd(ep.getCreditsSegment().getEnd());
            }
            
            segment.setIntroFingerprint(ep.getIntroFingerprint());
            segment.setCreditsFingerprint(ep.getCreditsFingerprint());

            if (isNew) {
                episodeSegmentMapper.insert(segment);
            } else {
                episodeSegmentMapper.updateById(segment);
            }
        }
    }
}
