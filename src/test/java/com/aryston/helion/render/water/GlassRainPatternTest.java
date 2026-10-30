package com.aryston.helion.render.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GlassRainPatternTest {
    private static final int BLUE = 2;
    private static final int BEAD_LEVELS = 10;
    private static final int STREAK_LEVELS = 9;
    private static final double MIN_BEAD_COVERAGE = 0.25;
    private static final double MAX_BEAD_COVERAGE = 0.5;
    private static final double MIN_STREAK_COVERAGE = 0.1;
    private static final double MAX_STREAK_COVERAGE = 0.4;
    private static final int CHANNEL_MASK = 0xFF;

    @Test
    void mipChainsStopAtOneTexelWideLevels() {
        GlassRainImage beads = GlassRainPattern.beads();
        GlassRainImage streaks = GlassRainPattern.streaks();
        assertEquals(BEAD_LEVELS, beads.levels().size());
        assertEquals(STREAK_LEVELS, streaks.levels().size());
        for (GlassRainImage image : new GlassRainImage[] {beads, streaks}) {
            for (int level = 0; level < image.levels().size(); level++) {
                assertEquals(image.levelWidth(level) * image.levelHeight(level) * GlassRainImage.CHANNELS, image.levels().get(level).length);
            }
        }
    }

    @Test
    void beadsCoverAboutTwoFifthsOfThePane() {
        double coverage = coverage(GlassRainPattern.beads());
        assertTrue(coverage > MIN_BEAD_COVERAGE && coverage < MAX_BEAD_COVERAGE, "bead coverage " + coverage);
    }

    @Test
    void streaksLeaveMostOfThePaneDry() {
        double coverage = coverage(GlassRainPattern.streaks());
        assertTrue(coverage > MIN_STREAK_COVERAGE && coverage < MAX_STREAK_COVERAGE, "streak coverage " + coverage);
    }

    private static double coverage(GlassRainImage image) {
        byte[] texels = image.levels().getFirst();
        int wet = 0;
        for (int index = 0; index < texels.length; index += GlassRainImage.CHANNELS) {
            if ((texels[index + BLUE] & CHANNEL_MASK) > 0) {
                wet++;
            }
        }
        return (double) wet / (texels.length / GlassRainImage.CHANNELS);
    }
}
