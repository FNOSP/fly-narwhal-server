package com.jankinwu.flynarwhal.core.ffmpeg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal JPEG dimension reader: walks the marker segments and returns the
 * width/height from the first Start-Of-Frame header. Used to measure the
 * generated sprite sheets without ImageIO, whose codec plugin discovery is
 * reflection-heavy in the native image.
 */
final class JpegDimensions {

    private JpegDimensions() {
    }

    /**
     * @return {width, height} of the image, or null when the file is not a
     *         parseable JPEG.
     */
    static int[] read(Path jpeg) {
        try (InputStream in = Files.newInputStream(jpeg)) {
            if (in.read() != 0xFF || in.read() != 0xD8) {
                return null; // not a JPEG (no SOI)
            }
            int marker;
            while ((marker = in.read()) != -1) {
                if (marker != 0xFF) {
                    return null; // corrupt: markers must be 0xFF-prefixed
                }
                int type = in.read();
                if (type == -1) {
                    return null;
                }
                // Standalone markers (RSTn, SOI, EOI, TEM) carry no length field.
                if (type == 0xD8 || type == 0x01 || (type >= 0xD0 && type <= 0xD9)) {
                    continue;
                }
                int lenHi = in.read();
                int lenLo = in.read();
                if (lenHi == -1 || lenLo == -1) {
                    return null;
                }
                int segmentLength = (lenHi << 8) | lenLo; // includes the two length bytes
                if (segmentLength < 2) {
                    return null;
                }
                if (isStartOfFrame(type)) {
                    // SOF payload: precision(1) height(2) width(2)
                    int precision = in.read();
                    int hHi = in.read();
                    int hLo = in.read();
                    int wHi = in.read();
                    int wLo = in.read();
                    if (precision == -1 || hHi == -1 || hLo == -1 || wHi == -1 || wLo == -1) {
                        return null;
                    }
                    int height = (hHi << 8) | hLo;
                    int width = (wHi << 8) | wLo;
                    return (width > 0 && height > 0) ? new int[]{width, height} : null;
                }
                long skipped = in.skip(segmentLength - 2L);
                if (skipped < segmentLength - 2L) {
                    return null; // truncated segment
                }
            }
            return null; // EOI reached without a SOF
        } catch (IOException e) {
            return null;
        }
    }

    /** SOF0-SOF15 except DHT(C4), JPG(C8) and DAC(CC). */
    private static boolean isStartOfFrame(int type) {
        return type >= 0xC0 && type <= 0xCF
                && type != 0xC4 && type != 0xC8 && type != 0xCC;
    }
}
