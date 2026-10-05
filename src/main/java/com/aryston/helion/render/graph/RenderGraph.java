package com.aryston.helion.render.graph;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;

public final class RenderGraph {
    private static final String PASS_PREFIX = "helion:";

    private final FrameGraphBuilder builder;
    private final GpuTimings timings;

    public RenderGraph(FrameGraphBuilder builder, GpuTimings timings) {
        this.builder = builder;
        this.timings = timings;
    }

    public FrameGraphBuilder builder() {
        return builder;
    }

    public FramePass addPass(String stage) {
        return builder.addPass(PASS_PREFIX + stage);
    }

    public Runnable timed(String stage, Runnable task) {
        return timings.measure(stage, task);
    }
}
