package com.aryston.helion.render.clouds;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;
import java.util.Optional;

record CloudPrograms(
    CompiledRenderPipeline noise,
    CompiledRenderPipeline march,
    CompiledRenderPipeline resolve,
    CompiledRenderPipeline composite
) {
    static Optional<CloudPrograms> compile() {
        List<Optional<CompiledRenderPipeline>> compiled = CloudPipelines.all().stream()
            .map(HelionPipelines::compiled)
            .toList();
        if (compiled.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(new CloudPrograms(
            get(CloudPipelines.NOISE),
            get(CloudPipelines.MARCH),
            get(CloudPipelines.RESOLVE),
            get(CloudPipelines.COMPOSITE)
        ));
    }

    private static CompiledRenderPipeline get(RenderPipeline pipeline) {
        return HelionPipelines.compiled(pipeline).orElseThrow();
    }
}
