package com.aryston.helion.render.dof;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.Optional;
import java.util.stream.Stream;

record DepthOfFieldPrograms(
    CompiledRenderPipeline focus,
    CompiledRenderPipeline downsample,
    CompiledRenderPipeline gather,
    CompiledRenderPipeline blend
) {
    static Optional<DepthOfFieldPrograms> compile() {
        Optional<CompiledRenderPipeline> focus = HelionPipelines.compiled(DepthOfFieldPipelines.FOCUS);
        Optional<CompiledRenderPipeline> downsample = HelionPipelines.compiled(DepthOfFieldPipelines.DOWNSAMPLE);
        Optional<CompiledRenderPipeline> gather = HelionPipelines.compiled(DepthOfFieldPipelines.GATHER);
        Optional<CompiledRenderPipeline> blend = HelionPipelines.compiled(DepthOfFieldPipelines.BLEND);
        if (Stream.of(focus, downsample, gather, blend).anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(new DepthOfFieldPrograms(focus.get(), downsample.get(), gather.get(), blend.get()));
    }
}
