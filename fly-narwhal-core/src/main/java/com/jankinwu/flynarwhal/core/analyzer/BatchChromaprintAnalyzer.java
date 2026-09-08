package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.AnalyzerAction;
import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import com.jankinwu.flynarwhal.core.data.SmartSkipConfig;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class BatchChromaprintAnalyzer implements MediaFileAnalyzer {

    private final ChromaprintAnalyzer chromaprintAnalyzer;
    private final SmartSkipConfig config;
    private final FFmpegWrapper ffmpegWrapper;

    /** Per-episode black frame scan cache for recap rebuilds, mirroring upstream _recapBlackFrameCache. */
    private final Map<String, List<BlackFrame>> recapBlackFrameCache = new HashMap<>();

    @Override
    public void analyze(List<QueuedEpisode> episodes, AnalysisMode mode) {
        // Chromaprint supports Introduction, Credits and Recap
        if (mode != AnalysisMode.INTRODUCTION && mode != AnalysisMode.CREDITS && mode != AnalysisMode.RECAP) {
            return;
        }
        log.info("Starting Chromaprint Analysis for {} episodes (Mode: {})", episodes.size(), mode);

        // 1. Generate fingerprints for all episodes that do not have one yet
        for (QueuedEpisode ep : episodes) {
            if (getFingerprint(ep, mode) != null) continue;
            try {
                int[] fp = chromaprintAnalyzer.getFingerprint(ep, mode);
                if (fp != null && fp.length > 0) {
                    setFingerprint(ep, mode, intsToBytes(fp));
                }
            } catch (Exception e) {
                ep.setAnalysisFailed(true);
                log.error("Error generating fingerprint for " + ep.getPath(), e);
            }
        }

        // 2. Compare episodes
        // We iterate through unanalyzed episodes and try to find a match against any other episode
        for (int i = 0; i < episodes.size(); i++) {
            QueuedEpisode current = episodes.get(i);
            if (current.isAnalyzed(mode)) continue;

            byte[] currentFpBytes = getFingerprint(current, mode);
            if (currentFpBytes == null || currentFpBytes.length == 0) continue;

            int[] currentFp = bytesToInts(currentFpBytes);

            for (int j = 0; j < episodes.size(); j++) {
                if (i == j) continue;
                QueuedEpisode other = episodes.get(j);

                byte[] otherFpBytes = getFingerprint(other, mode);
                if (otherFpBytes == null || otherFpBytes.length == 0) continue;

                int[] otherFp = bytesToInts(otherFpBytes);

                try {
                    Map<String, Segment> result = chromaprintAnalyzer.compareEpisodes(
                        current.getPath(), currentFp,
                        other.getPath(), otherFp,
                        mode, current.getDuration(), other.getDuration()
                    );

                    Segment seg = result.get(current.getPath());
                    if (seg != null && seg.isValid() && seg.getDuration() > 0) {
                        if (mode == AnalysisMode.RECAP) {
                            // A chromaprint match is only a candidate card: rebuild the
                            // recap segment from black frames bounded before the intro.
                            Segment recap = RecapDetectionHelper.buildRecapFromCandidate(
                                    current, seg, config, ffmpegWrapper, recapBlackFrameCache);
                            if (recap == null || !recap.isValid() || recap.getDuration() <= 0) {
                                continue; // try matching against another episode
                            }
                            seg = recap;
                        }
                        log.info("Found {} via Chromaprint for {}: {}-{}", mode, current.getPath(), seg.getStart(), seg.getEnd());
                        current.setSegment(mode, seg);
                        current.setAnalyzed(mode, true);
                        current.setAnalyzerAction(mode, AnalyzerAction.CHROMAPRINT);
                        break; // Found a match, move to next episode
                    }
                } catch (Exception e) {
                    current.setAnalysisFailed(true);
                    log.error("Error comparing episodes", e);
                }
            }
        }
    }

    private byte[] getFingerprint(QueuedEpisode episode, AnalysisMode mode) {
        switch (mode) {
            case INTRODUCTION: return episode.getIntroFingerprint();
            case CREDITS: return episode.getCreditsFingerprint();
            case RECAP: return episode.getRecapFingerprint();
            default: return null;
        }
    }

    private void setFingerprint(QueuedEpisode episode, AnalysisMode mode, byte[] fingerprint) {
        switch (mode) {
            case INTRODUCTION: episode.setIntroFingerprint(fingerprint); break;
            case CREDITS: episode.setCreditsFingerprint(fingerprint); break;
            case RECAP: episode.setRecapFingerprint(fingerprint); break;
        }
    }

    private byte[] intsToBytes(int[] ints) {
        if (ints == null) return null;
        ByteBuffer bb = ByteBuffer.allocate(ints.length * 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.asIntBuffer().put(ints);
        return bb.array();
    }

    private int[] bytesToInts(byte[] bytes) {
        if (bytes == null) return new int[0];
        IntBuffer ib = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int[] ints = new int[ib.remaining()];
        ib.get(ints);
        return ints;
    }
}
