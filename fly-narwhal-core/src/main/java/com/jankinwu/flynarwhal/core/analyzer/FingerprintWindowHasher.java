package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Fingerprint-window versioning, ported from upstream #971 (Preserve fingerprint
 * cache entries across processing setting changes).
 *
 * <p>A cached fingerprint BLOB is only valid for the window it was generated
 * from. Hashing the window bounds (rather than the whole config) invalidates the
 * cache exactly when a window-relevant setting changes — analysisPercent,
 * analysisLengthLimit, maximumCreditsDuration, maximumMovieCreditsDuration, or a
 * re-probed duration — while edits to unrelated processing settings (offsets,
 * snap thresholds, black-frame parameters...) keep every cached fingerprint,
 * which was the point of the upstream fix.
 *
 * <p>INTRODUCTION and RECAP share the intro window and therefore one hash;
 * CREDITS has its own.
 */
public final class FingerprintWindowHasher {

    /** Bump when the hash input format changes, so old rows invalidate once. */
    private static final String VERSION = "v1";

    private FingerprintWindowHasher() {
    }

    /**
     * The window hash a fingerprint for {@code episode} in {@code mode} must
     * carry to be reusable. Requires the episode's fingerprint window fields
     * (introFingerprintEnd / creditsFingerprintStart) to be populated from the
     * current config first.
     */
    public static String expectedHash(QueuedEpisode episode, AnalysisMode mode) {
        if (mode == AnalysisMode.CREDITS) {
            return hash(VERSION, "CREDITS", episode.getCreditsFingerprintStart(), episode.getDuration());
        }
        return hash(VERSION, "INTRO", 0.0, episode.getIntroFingerprintEnd());
    }

    private static String hash(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof Double d) {
                // Fixed precision so the same window hashes the same on every platform.
                sb.append(String.format(Locale.ROOT, "%.3f", d));
            } else {
                sb.append(part);
            }
            sb.append('|');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
