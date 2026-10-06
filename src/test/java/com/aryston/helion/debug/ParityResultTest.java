package com.aryston.helion.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParityResultTest {
    private static final int COLOR_BLOCK = 4;
    private static final int DEPTH_BLOCK = 4;
    private static final int PIXELS = 3;
    private static final int SECOND_PIXEL_GREEN = COLOR_BLOCK + 1;
    private static final int THIRD_PIXEL_BLUE = 2 * COLOR_BLOCK + 2;
    private static final int SMALL_DELTA = 5;
    private static final int FULL_DELTA = 255;

    @Test
    void identicalFramesMatch() {
        ParityResult result = compare(color(), color(), depth(), depth());

        assertTrue(result.isIdentical());
        assertEquals(PIXELS, result.pixels());
    }

    @Test
    void countsDifferentColorPixelsAndLargestChannelDelta() {
        byte[] changed = color();
        changed[SECOND_PIXEL_GREEN] = (byte) SMALL_DELTA;
        changed[THIRD_PIXEL_BLUE] = (byte) FULL_DELTA;

        ParityResult result = compare(color(), changed, depth(), depth());

        assertFalse(result.isIdentical());
        assertEquals(2, result.differentColorPixels());
        assertEquals(FULL_DELTA, result.maxColorDelta());
        assertEquals(0, result.differentDepthPixels());
    }

    @Test
    void countsDifferentDepthPixels() {
        byte[] changed = depth();
        changed[0] = 1;

        ParityResult result = compare(color(), color(), depth(), changed);

        assertFalse(result.isIdentical());
        assertEquals(0, result.differentColorPixels());
        assertEquals(1, result.differentDepthPixels());
    }

    private static ParityResult compare(byte[] colorA, byte[] colorB, byte[] depthA, byte[] depthB) {
        return ParityResult.compare(colorA, colorB, COLOR_BLOCK, depthA, depthB, DEPTH_BLOCK);
    }

    private static byte[] color() {
        return new byte[PIXELS * COLOR_BLOCK];
    }

    private static byte[] depth() {
        return new byte[PIXELS * DEPTH_BLOCK];
    }
}
