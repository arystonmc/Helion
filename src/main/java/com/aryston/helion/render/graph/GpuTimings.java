package com.aryston.helion.render.graph;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

public final class GpuTimings {
    private static final String LABEL = "Helion GPU Timings";
    private static final int FRAMES_IN_FLIGHT = 4;
    private static final int MAX_STAGES_PER_FRAME = 16;
    private static final int QUERIES_PER_STAGE = 2;
    private static final int QUERIES_PER_FRAME = MAX_STAGES_PER_FRAME * QUERIES_PER_STAGE;
    private static final int QUERY_BYTES = Long.BYTES;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final double SMOOTHING = 0.1;

    private final GpuResources resources;
    private final List<List<String>> stagesPerFrame = new ArrayList<>();
    private final Map<String, Double> averageMillis = new LinkedHashMap<>();
    private @Nullable GpuQueryPool pool;
    private @Nullable TrackedResource tracked;
    private double nanosPerTick;
    private int frame;

    public GpuTimings(GpuResources resources) {
        this.resources = resources;
        for (int slot = 0; slot < FRAMES_IN_FLIGHT; slot++) {
            stagesPerFrame.add(new ArrayList<>());
        }
    }

    public void beginFrame(float timestampPeriod) {
        if (pool == null) {
            open(timestampPeriod);
        }
        frame = (frame + 1) % FRAMES_IN_FLIGHT;
        collect(frame);
        stagesPerFrame.get(frame).clear();
    }

    public Runnable measure(String stage, Runnable task) {
        GpuQueryPool queries = pool;
        List<String> stages = stagesPerFrame.get(frame);
        if (queries == null || stages.size() >= MAX_STAGES_PER_FRAME) {
            return task;
        }
        int begin = frame * QUERIES_PER_FRAME + stages.size() * QUERIES_PER_STAGE;
        stages.add(stage);
        return () -> {
            RenderSystem.getDevice().createCommandEncoder().writeTimestamp(queries, begin);
            task.run();
            RenderSystem.getDevice().createCommandEncoder().writeTimestamp(queries, begin + 1);
        };
    }

    public Map<String, Double> averageMillis() {
        return Collections.unmodifiableMap(averageMillis);
    }

    public void close() {
        if (tracked != null) {
            resources.release(tracked);
        }
        pool = null;
        tracked = null;
        averageMillis.clear();
        stagesPerFrame.forEach(List::clear);
    }

    private void open(float timestampPeriod) {
        int size = FRAMES_IN_FLIGHT * QUERIES_PER_FRAME;
        GpuQueryPool created = RenderSystem.getDevice().createTimestampQueryPool(size);
        pool = created;
        nanosPerTick = timestampPeriod;
        tracked = resources.track(LABEL, (long) size * QUERY_BYTES, created::close);
    }

    private void collect(int slot) {
        List<String> stages = stagesPerFrame.get(slot);
        if (pool == null || stages.isEmpty()) {
            return;
        }
        OptionalLong[] values = pool.getValues(slot * QUERIES_PER_FRAME, stages.size() * QUERIES_PER_STAGE);
        for (int index = 0; index < stages.size(); index++) {
            OptionalLong begin = values[index * QUERIES_PER_STAGE];
            OptionalLong end = values[index * QUERIES_PER_STAGE + 1];
            if (begin.isPresent() && end.isPresent()) {
                record(stages.get(index), (end.getAsLong() - begin.getAsLong()) * nanosPerTick / NANOS_PER_MILLI);
            }
        }
    }

    private void record(String stage, double millis) {
        averageMillis.merge(stage, millis, (previous, latest) -> previous + (latest - previous) * SMOOTHING);
    }
}
