package com.aryston.helion.render.lighting;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.Optional;

record AmbientOcclusionPrograms(
    CompiledRenderPipeline viewDepth,
    CompiledRenderPipeline horizonSearch,
    CompiledRenderPipeline denoise,
    CompiledRenderPipeline denoiseAndResolve,
    CompiledRenderPipeline apply,
    CompiledRenderPipeline debugView
) {
    static Optional<AmbientOcclusionPrograms> compile() {
        Optional<CompiledRenderPipeline> viewDepth = HelionPipelines.compiled(AmbientOcclusionPipelines.VIEW_DEPTH);
        Optional<CompiledRenderPipeline> horizonSearch = HelionPipelines.compiled(AmbientOcclusionPipelines.HORIZON_SEARCH);
        Optional<CompiledRenderPipeline> denoise = HelionPipelines.compiled(AmbientOcclusionPipelines.DENOISE);
        Optional<CompiledRenderPipeline> denoiseAndResolve = HelionPipelines.compiled(AmbientOcclusionPipelines.DENOISE_AND_RESOLVE);
        Optional<CompiledRenderPipeline> apply = HelionPipelines.compiled(AmbientOcclusionPipelines.APPLY);
        Optional<CompiledRenderPipeline> debugView = HelionPipelines.compiled(AmbientOcclusionPipelines.DEBUG_VIEW);
        if (viewDepth.isEmpty() || horizonSearch.isEmpty() || denoise.isEmpty()
            || denoiseAndResolve.isEmpty() || apply.isEmpty() || debugView.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AmbientOcclusionPrograms(
            viewDepth.get(), horizonSearch.get(), denoise.get(), denoiseAndResolve.get(), apply.get(), debugView.get()
        ));
    }
}
