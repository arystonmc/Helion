package com.aryston.helion.render.voxel;

import static com.aryston.helion.Helion.LOGGER;

import java.util.Locale;

public final class VoxelBuildStatistics {
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final double AVERAGE_WEIGHT = 0.05;
    private static final String CPU_FORMAT = "%.2f";
    private static final String WALL_FORMAT = "%.1f";
    private static final String FRAME_FORMAT = "%.3f";

    private boolean filling;
    private long fillStartNanos;
    private long fillCpuNanos;
    private int fillFrames;
    private int fillSections;
    private double lastFrameMillis;
    private double averageFrameMillis;
    private double maxFillFrameMillis;
    private double lastFillCpuMillis;
    private double lastFillWallMillis;
    private int lastFrameSections;

    void beginFrame(int pending, long startNanos) {
        if (!filling && pending == VoxelVolumeLayout.SECTION_COUNT) {
            filling = true;
            fillStartNanos = startNanos;
            fillCpuNanos = 0L;
            fillFrames = 0;
            fillSections = 0;
            maxFillFrameMillis = 0.0;
        }
    }

    void endFrame(int built, int pending, long cpuNanos) {
        lastFrameSections = built;
        lastFrameMillis = cpuNanos / NANOS_PER_MILLI;
        averageFrameMillis += (lastFrameMillis - averageFrameMillis) * AVERAGE_WEIGHT;
        if (!filling) {
            return;
        }
        fillCpuNanos += cpuNanos;
        fillFrames++;
        fillSections += built;
        maxFillFrameMillis = Math.max(maxFillFrameMillis, lastFrameMillis);
        if (pending == 0) {
            filling = false;
            lastFillCpuMillis = fillCpuNanos / NANOS_PER_MILLI;
            lastFillWallMillis = (System.nanoTime() - fillStartNanos) / NANOS_PER_MILLI;
            LOGGER.info(
                "Helion voxel volume filled {} sections in {} frames: {} ms CPU, {} ms wall, at most {} ms per frame",
                fillSections, fillFrames, String.format(Locale.ROOT, CPU_FORMAT, lastFillCpuMillis), String.format(Locale.ROOT, WALL_FORMAT, lastFillWallMillis),
                String.format(Locale.ROOT, FRAME_FORMAT, maxFillFrameMillis)
            );
        }
    }

    void reset() {
        filling = false;
        lastFrameMillis = 0.0;
        averageFrameMillis = 0.0;
        lastFrameSections = 0;
    }

    public double lastFrameMillis() {
        return lastFrameMillis;
    }

    public double averageFrameMillis() {
        return averageFrameMillis;
    }

    public int lastFrameSections() {
        return lastFrameSections;
    }

    public double lastFillCpuMillis() {
        return lastFillCpuMillis;
    }

    public double lastFillWallMillis() {
        return lastFillWallMillis;
    }

    public double maxFillFrameMillis() {
        return maxFillFrameMillis;
    }
}
