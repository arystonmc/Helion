package com.aryston.helion.render.motion;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.Optional;
import java.util.stream.Stream;

record MotionBlurPrograms(
    CompiledRenderPipeline tileMax,
    CompiledRenderPipeline neighborMax,
    CompiledRenderPipeline gather,
    CompiledRenderPipeline apply
) {
    static Optional<MotionBlurPrograms> compile() {
        Optional<CompiledRenderPipeline> tileMax = HelionPipelines.compiled(MotionBlurPipelines.TILE_MAX);
        Optional<CompiledRenderPipeline> neighborMax = HelionPipelines.compiled(MotionBlurPipelines.NEIGHBOR_MAX);
        Optional<CompiledRenderPipeline> gather = HelionPipelines.compiled(MotionBlurPipelines.GATHER);
        Optional<CompiledRenderPipeline> apply = HelionPipelines.compiled(MotionBlurPipelines.APPLY);
        if (Stream.of(tileMax, neighborMax, gather, apply).anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(new MotionBlurPrograms(tileMax.get(), neighborMax.get(), gather.get(), apply.get()));
    }
}
