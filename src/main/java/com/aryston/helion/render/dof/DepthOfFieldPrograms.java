package com.aryston.helion.render.dof;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.Optional;
import java.util.stream.Stream;

record DepthOfFieldPrograms(
    CompiledRenderPipeline focus,
    CompiledRenderPipeline downsample,
    CompiledRenderPipeline downsampleTerrainDepth,
    CompiledRenderPipeline gather,
    CompiledRenderPipeline blend,
    CompiledRenderPipeline blendTerrainDepth
) {
    static Optional<DepthOfFieldPrograms> compile() {
        Optional<CompiledRenderPipeline> focus = HelionPipelines.compiled(DepthOfFieldPipelines.FOCUS);
        Optional<CompiledRenderPipeline> downsample = HelionPipelines.compiled(DepthOfFieldPipelines.DOWNSAMPLE);
        Optional<CompiledRenderPipeline> gather = HelionPipelines.compiled(DepthOfFieldPipelines.GATHER);
        Optional<CompiledRenderPipeline> blend = HelionPipelines.compiled(DepthOfFieldPipelines.BLEND);
        Optional<CompiledRenderPipeline> downsampleTerrainDepth = HelionPipelines.compiled(DepthOfFieldPipelines.DOWNSAMPLE_TERRAIN_DEPTH);
        Optional<CompiledRenderPipeline> blendTerrainDepth = HelionPipelines.compiled(DepthOfFieldPipelines.BLEND_TERRAIN_DEPTH);
        if (Stream.of(focus, downsample, downsampleTerrainDepth, gather, blend, blendTerrainDepth).anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(new DepthOfFieldPrograms(
            focus.get(), downsample.get(), downsampleTerrainDepth.get(), gather.get(), blend.get(), blendTerrainDepth.get()
        ));
    }
}
