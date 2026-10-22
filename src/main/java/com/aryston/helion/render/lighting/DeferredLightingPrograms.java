package com.aryston.helion.render.lighting;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;
import java.util.Optional;

record DeferredLightingPrograms(
    CompiledRenderPipeline shading,
    CompiledRenderPipeline lightOnlyView,
    CompiledRenderPipeline physicalSkyShading,
    CompiledRenderPipeline physicalSkyLightOnlyView,
    CompiledRenderPipeline shadowedShading,
    CompiledRenderPipeline shadowedLightOnlyView
) {
    static Optional<DeferredLightingPrograms> compile() {
        return compile(DeferredLightingPipelines.standard());
    }

    static Optional<DeferredLightingPrograms> compileVoxel() {
        return compile(DeferredLightingPipelines.voxel());
    }

    private static Optional<DeferredLightingPrograms> compile(List<RenderPipeline> pipelines) {
        if (pipelines.stream().anyMatch(pipeline -> HelionPipelines.compiled(pipeline).isEmpty())) {
            return Optional.empty();
        }
        List<CompiledRenderPipeline> compiled = pipelines.stream().map(DeferredLightingPrograms::get).toList();
        return Optional.of(new DeferredLightingPrograms(
            compiled.get(0), compiled.get(1), compiled.get(2), compiled.get(3), compiled.get(4), compiled.get(5)
        ));
    }

    private static CompiledRenderPipeline get(RenderPipeline pipeline) {
        return HelionPipelines.compiled(pipeline).orElseThrow();
    }
}
