package com.jankinwu.flynarwhal.core.data;

/**
 * Outcome of one sprite-sheet ffmpeg run: how many sheets were actually
 * produced and the per-thumbnail dimensions measured from the first sheet
 * (scale=W:-2 keeps the aspect, so the height varies per source).
 */
public record SpriteSheetResult(int sheetCount, int thumbWidth, int thumbHeight) {
}
