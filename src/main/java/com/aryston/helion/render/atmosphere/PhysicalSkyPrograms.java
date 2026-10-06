package com.aryston.helion.render.atmosphere;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;
import java.util.Optional;

record PhysicalSkyPrograms(
    CompiledRenderPipeline transmittance,
    CompiledRenderPipeline multipleScattering,
    CompiledRenderPipeline sunSkyView,
    CompiledRenderPipeline moonSkyView,
    CompiledRenderPipeline sky,
    CompiledRenderPipeline fog,
    CompiledRenderPipeline horizon,
    CompiledRenderPipeline skyLight
) {
    static Optional<PhysicalSkyPrograms> compile() {
        List<Optional<CompiledRenderPipeline>> compiled = PhysicalSkyPipelines.all().stream()
            .map(HelionPipelines::compiled)
            .toList();
        if (compiled.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(new PhysicalSkyPrograms(
            get(PhysicalSkyPipelines.TRANSMITTANCE),
            get(PhysicalSkyPipelines.MULTIPLE_SCATTERING),
            get(PhysicalSkyPipelines.SUN_SKY_VIEW),
            get(PhysicalSkyPipelines.MOON_SKY_VIEW),
            get(PhysicalSkyPipelines.SKY),
            get(PhysicalSkyPipelines.FOG_PASS),
            get(PhysicalSkyPipelines.HORIZON),
            get(PhysicalSkyPipelines.SKY_LIGHT)
        ));
    }

    private static CompiledRenderPipeline get(RenderPipeline pipeline) {
        return HelionPipelines.compiled(pipeline).orElseThrow();
    }
}
