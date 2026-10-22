package com.aryston.helion.render.lightshaft;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;
import java.util.Optional;

record LightShaftPrograms(
    CompiledRenderPipeline mask,
    CompiledRenderPipeline cloudMask,
    CompiledRenderPipeline blur,
    CompiledRenderPipeline composite
) {
    static Optional<LightShaftPrograms> compile() {
        List<Optional<CompiledRenderPipeline>> compiled = LightShaftPipelines.all().stream()
            .map(HelionPipelines::compiled)
            .toList();
        if (compiled.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(new LightShaftPrograms(
            get(LightShaftPipelines.MASK),
            get(LightShaftPipelines.CLOUD_MASK),
            get(LightShaftPipelines.BLUR),
            get(LightShaftPipelines.COMPOSITE)
        ));
    }

    private static CompiledRenderPipeline get(RenderPipeline pipeline) {
        return HelionPipelines.compiled(pipeline).orElseThrow();
    }
}
