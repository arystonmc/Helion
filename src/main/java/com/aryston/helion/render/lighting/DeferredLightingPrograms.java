package com.aryston.helion.render.lighting;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.Optional;

record DeferredLightingPrograms(
    CompiledRenderPipeline light,
    CompiledRenderPipeline physicalSkyLight,
    CompiledRenderPipeline shading,
    CompiledRenderPipeline lightOnlyView,
    CompiledRenderPipeline shadowedShading,
    CompiledRenderPipeline shadowedLightOnlyView
) {
    static Optional<DeferredLightingPrograms> compile() {
        if (DeferredLightingPipelines.all().stream().anyMatch(pipeline -> HelionPipelines.compiled(pipeline).isEmpty())) {
            return Optional.empty();
        }
        return Optional.of(new DeferredLightingPrograms(
            get(DeferredLightingPipelines.LIGHT),
            get(DeferredLightingPipelines.PHYSICAL_SKY_LIGHT),
            get(DeferredLightingPipelines.SHADING),
            get(DeferredLightingPipelines.LIGHT_ONLY_VIEW),
            get(DeferredLightingPipelines.SHADOWED_SHADING),
            get(DeferredLightingPipelines.SHADOWED_LIGHT_ONLY_VIEW)
        ));
    }

    private static CompiledRenderPipeline get(RenderPipeline pipeline) {
        return HelionPipelines.compiled(pipeline).orElseThrow();
    }
}
