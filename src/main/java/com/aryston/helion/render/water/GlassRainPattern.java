package com.aryston.helion.render.water;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

final class GlassRainPattern {
    static final int BEAD_SIZE = 512;
    static final int STREAK_WIDTH = 256;
    static final int STREAK_HEIGHT = 512;
    static final float BEAD_REACH_TEXELS = 12.0F;
    static final float STREAK_REACH_TEXELS = 6.0F;

    private static final long BEAD_SEED = 0x5EEDB3ADL;
    private static final long STREAK_SEED = 0x57AEA4L;
    private static final float BEAD_MIN_RADIUS = 2.2F;
    private static final float BEAD_MAX_RADIUS = BEAD_REACH_TEXELS;
    private static final double BEAD_SIZE_BIAS = 2.4;
    private static final int BEAD_ATTEMPTS = 40000;
    private static final double BEAD_COVERAGE = 0.42;
    private static final float BEAD_GAP = 1.0F;
    private static final float BEAD_SQUASH = 0.9F;
    private static final float BEAD_WOBBLE = 0.08F;
    private static final int BEAD_LOBES = 3;
    private static final int GRID_CELL = 32;
    private static final float SEED_REACH = 10.0F;
    private static final int STREAK_PATHS = 6;
    private static final double STREAK_JITTER = 0.3;
    private static final double STREAK_WANDER_MIN = 1.0;
    private static final double STREAK_WANDER_MAX = 3.0;
    private static final double STREAK_RIPPLE_MIN = 0.5;
    private static final double STREAK_RIPPLE_MAX = 1.2;
    private static final double STREAK_WIDTH_MIN = 3.5;
    private static final double STREAK_WIDTH_MAX = 5.5;
    private static final double STREAK_SWELL = 0.25;
    private static final int STREAK_WANDER_WAVES = 2;
    private static final int STREAK_RIPPLE_WAVES_MIN = 3;
    private static final int STREAK_RIPPLE_WAVES_MAX = 6;
    private static final int STREAK_SWELL_WAVES_MIN = 2;
    private static final int STREAK_SWELL_WAVES_MAX = 5;
    private static final double TURN = Math.PI * 2.0;
    private static final float HALF = 0.5F;
    private static final float CHANNEL_MAX = 255.0F;
    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int BLUE = 2;
    private static final int ALPHA = 3;
    private static final float FLAT = 0.0F;
    private static final float NO_SEED = 0.5F;

    private GlassRainPattern() {
    }

    static GlassRainImage beads() {
        float[] texels = blank(BEAD_SIZE, BEAD_SIZE);
        float[] nearest = farAway(BEAD_SIZE * BEAD_SIZE);
        for (Bead bead : placeBeads(new SplittableRandom(BEAD_SEED))) {
            drawBead(texels, nearest, bead);
        }
        return withMipmaps(texels, BEAD_SIZE, BEAD_SIZE);
    }

    static GlassRainImage streaks() {
        SplittableRandom random = new SplittableRandom(STREAK_SEED);
        List<Streak> streaks = new ArrayList<>();
        for (int index = 0; index < STREAK_PATHS; index++) {
            streaks.add(Streak.random(random, index));
        }
        float[] texels = blank(STREAK_WIDTH, STREAK_HEIGHT);
        for (int row = 0; row < STREAK_HEIGHT; row++) {
            drawStreakRow(texels, streaks, row);
        }
        return withMipmaps(texels, STREAK_WIDTH, STREAK_HEIGHT);
    }

    private static List<Bead> placeBeads(SplittableRandom random) {
        List<Bead> beads = new ArrayList<>();
        int cells = BEAD_SIZE / GRID_CELL;
        List<List<Bead>> grid = new ArrayList<>();
        for (int cell = 0; cell < cells * cells; cell++) {
            grid.add(new ArrayList<>());
        }
        double covered = 0.0;
        double target = BEAD_COVERAGE * BEAD_SIZE * BEAD_SIZE;
        for (int attempt = 0; attempt < BEAD_ATTEMPTS && covered < target; attempt++) {
            float radius = (float) (BEAD_MIN_RADIUS * Math.pow(BEAD_MAX_RADIUS / BEAD_MIN_RADIUS, Math.pow(random.nextDouble(), BEAD_SIZE_BIAS)));
            Bead bead = new Bead(random.nextFloat() * BEAD_SIZE, random.nextFloat() * BEAD_SIZE, radius, random.nextFloat(), random.nextFloat());
            int cellX = (int) (bead.x / GRID_CELL);
            int cellY = (int) (bead.y / GRID_CELL);
            if (overlaps(grid, cells, cellX, cellY, bead)) {
                continue;
            }
            grid.get(cellY * cells + cellX).add(bead);
            beads.add(bead);
            covered += Math.PI * radius * radius * BEAD_SQUASH;
        }
        return beads;
    }

    private static boolean overlaps(List<List<Bead>> grid, int cells, int cellX, int cellY, Bead bead) {
        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                int neighbor = Math.floorMod(cellY + offsetY, cells) * cells + Math.floorMod(cellX + offsetX, cells);
                for (Bead other : grid.get(neighbor)) {
                    float apartX = wrapped(other.x - bead.x, BEAD_SIZE);
                    float apartY = wrapped(other.y - bead.y, BEAD_SIZE);
                    float reach = other.radius + bead.radius + BEAD_GAP;
                    if (apartX * apartX + apartY * apartY < reach * reach) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void drawBead(float[] texels, float[] nearest, Bead bead) {
        int reach = (int) Math.ceil(bead.radius + SEED_REACH);
        int originX = (int) Math.floor(bead.x);
        int originY = (int) Math.floor(bead.y);
        for (int offsetY = -reach; offsetY <= reach; offsetY++) {
            for (int offsetX = -reach; offsetX <= reach; offsetX++) {
                int texelX = Math.floorMod(originX + offsetX, BEAD_SIZE);
                int texelY = Math.floorMod(originY + offsetY, BEAD_SIZE);
                float localX = wrapped(texelX + HALF - bead.x, BEAD_SIZE) / bead.radius;
                float localY = wrapped(texelY + HALF - bead.y, BEAD_SIZE) / (bead.radius * BEAD_SQUASH);
                drawBeadTexel(texels, nearest, texelY * BEAD_SIZE + texelX, localX, localY, bead);
            }
        }
    }

    private static void drawBeadTexel(float[] texels, float[] nearest, int index, float localX, float localY, Bead bead) {
        float length = (float) Math.sqrt(localX * localX + localY * localY);
        float distance = length * (1.0F + BEAD_WOBBLE * (float) Math.sin(Math.atan2(localY, localX) * BEAD_LOBES + bead.shape * TURN));
        float outside = (distance - 1.0F) * bead.radius;
        if (outside >= nearest[index]) {
            return;
        }
        nearest[index] = Math.max(outside, FLAT);
        texels[index * GlassRainImage.CHANNELS + ALPHA] = bead.seed;
        if (distance >= 1.0F) {
            return;
        }
        float lens = length > FLAT ? distance / length * (bead.radius / BEAD_REACH_TEXELS) : FLAT;
        put(texels, index, localX * lens, localY * lens, 1.0F - distance, bead.seed);
    }

    private static void drawStreakRow(float[] texels, List<Streak> streaks, int row) {
        for (int column = 0; column < STREAK_WIDTH; column++) {
            int index = row * STREAK_WIDTH + column;
            float closest = Float.MAX_VALUE;
            for (Streak streak : streaks) {
                float across = wrapped(column + HALF - streak.center(row), STREAK_WIDTH);
                float width = streak.width(row);
                if (Math.abs(across) < closest) {
                    closest = Math.abs(across);
                    texels[index * GlassRainImage.CHANNELS + ALPHA] = streak.seed;
                }
                if (Math.abs(across) < width) {
                    float inside = across / width;
                    put(texels, index, inside * (width / STREAK_REACH_TEXELS), FLAT, 1.0F - Math.abs(inside), streak.seed);
                }
            }
        }
    }

    private static void put(float[] texels, int index, float normalX, float normalY, float profile, float seed) {
        int base = index * GlassRainImage.CHANNELS;
        texels[base + RED] = HALF + HALF * normalX;
        texels[base + GREEN] = HALF + HALF * normalY;
        texels[base + BLUE] = profile;
        texels[base + ALPHA] = seed;
    }

    private static float[] blank(int width, int height) {
        float[] texels = new float[width * height * GlassRainImage.CHANNELS];
        for (int index = 0; index < width * height; index++) {
            put(texels, index, FLAT, FLAT, FLAT, NO_SEED);
        }
        return texels;
    }

    private static float[] farAway(int count) {
        float[] distances = new float[count];
        Arrays.fill(distances, SEED_REACH);
        return distances;
    }

    private static float wrapped(float offset, int period) {
        return offset - period * Math.round(offset / period);
    }

    private static GlassRainImage withMipmaps(float[] base, int width, int height) {
        List<byte[]> levels = new ArrayList<>();
        float[] level = base;
        int levelWidth = width;
        int levelHeight = height;
        levels.add(quantized(level));
        while (levelWidth > 1 && levelHeight > 1) {
            int nextWidth = Math.max(1, levelWidth / 2);
            int nextHeight = Math.max(1, levelHeight / 2);
            level = halved(level, levelWidth, levelHeight, nextWidth, nextHeight);
            levelWidth = nextWidth;
            levelHeight = nextHeight;
            levels.add(quantized(level));
        }
        return new GlassRainImage(width, height, levels);
    }

    private static float[] halved(float[] source, int width, int height, int nextWidth, int nextHeight) {
        float[] target = new float[nextWidth * nextHeight * GlassRainImage.CHANNELS];
        int stepX = width / nextWidth;
        int stepY = height / nextHeight;
        float share = 1.0F / (stepX * stepY);
        for (int y = 0; y < nextHeight; y++) {
            for (int x = 0; x < nextWidth; x++) {
                for (int sampleY = 0; sampleY < stepY; sampleY++) {
                    for (int sampleX = 0; sampleX < stepX; sampleX++) {
                        int from = ((y * stepY + sampleY) * width + x * stepX + sampleX) * GlassRainImage.CHANNELS;
                        int to = (y * nextWidth + x) * GlassRainImage.CHANNELS;
                        for (int channel = 0; channel < GlassRainImage.CHANNELS; channel++) {
                            target[to + channel] += source[from + channel] * share;
                        }
                    }
                }
            }
        }
        return target;
    }

    private static byte[] quantized(float[] texels) {
        byte[] bytes = new byte[texels.length];
        for (int index = 0; index < texels.length; index++) {
            bytes[index] = (byte) Math.round(Math.clamp(texels[index], 0.0F, 1.0F) * CHANNEL_MAX);
        }
        return bytes;
    }

    private record Bead(float x, float y, float radius, float seed, float shape) {
    }

    private record Streak(float origin, float wander, int wanderWaves, float wanderPhase, float ripple, int rippleWaves, float ripplePhase, float baseWidth, int swellWaves, float swellPhase, float seed) {
        static Streak random(SplittableRandom random, int index) {
            float slot = (float) STREAK_WIDTH / STREAK_PATHS;
            return new Streak(
                (index + HALF + (float) random.nextDouble(-STREAK_JITTER, STREAK_JITTER)) * slot,
                (float) random.nextDouble(STREAK_WANDER_MIN, STREAK_WANDER_MAX),
                random.nextInt(1, STREAK_WANDER_WAVES + 1),
                (float) (random.nextDouble() * TURN),
                (float) random.nextDouble(STREAK_RIPPLE_MIN, STREAK_RIPPLE_MAX),
                random.nextInt(STREAK_RIPPLE_WAVES_MIN, STREAK_RIPPLE_WAVES_MAX + 1),
                (float) (random.nextDouble() * TURN),
                (float) random.nextDouble(STREAK_WIDTH_MIN, STREAK_WIDTH_MAX),
                random.nextInt(STREAK_SWELL_WAVES_MIN, STREAK_SWELL_WAVES_MAX + 1),
                (float) (random.nextDouble() * TURN),
                random.nextFloat()
            );
        }

        float center(int row) {
            double along = (row + HALF) / STREAK_HEIGHT * TURN;
            return origin + wander * (float) Math.sin(along * wanderWaves + wanderPhase) + ripple * (float) Math.sin(along * rippleWaves + ripplePhase);
        }

        float width(int row) {
            double along = (row + HALF) / STREAK_HEIGHT * TURN;
            return baseWidth * (1.0F + (float) (STREAK_SWELL * Math.sin(along * swellWaves + swellPhase)));
        }
    }
}
