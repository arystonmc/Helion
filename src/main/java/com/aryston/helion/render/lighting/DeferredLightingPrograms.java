package com.aryston.helion.render.lighting;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.Optional;

record DeferredLightingPrograms(CompiledRenderPipeline light, CompiledRenderPipeline shading, CompiledRenderPipeline lightOnlyView) {
    static Optional<DeferredLightingPrograms> compile() {
        Optional<CompiledRenderPipeline> light = HelionPipelines.compiled(DeferredLightingPipelines.LIGHT);
        Optional<CompiledRenderPipeline> shading = HelionPipelines.compiled(DeferredLightingPipelines.SHADING);
        Optional<CompiledRenderPipeline> lightOnlyView = HelionPipelines.compiled(DeferredLightingPipelines.LIGHT_ONLY_VIEW);
        if (light.isEmpty() || shading.isEmpty() || lightOnlyView.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DeferredLightingPrograms(light.get(), shading.get(), lightOnlyView.get()));
    }
}
