package com.aryston.helion.render.temporal;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.Optional;

record TemporalPrograms(CompiledRenderPipeline resolve, CompiledRenderPipeline resolveMovingObjects, CompiledRenderPipeline apply) {
    static Optional<TemporalPrograms> compile() {
        Optional<CompiledRenderPipeline> resolve = HelionPipelines.compiled(TemporalPipelines.RESOLVE);
        Optional<CompiledRenderPipeline> resolveMovingObjects = HelionPipelines.compiled(TemporalPipelines.RESOLVE_MOVING_OBJECTS);
        Optional<CompiledRenderPipeline> apply = HelionPipelines.compiled(TemporalPipelines.APPLY);
        if (resolve.isEmpty() || resolveMovingObjects.isEmpty() || apply.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TemporalPrograms(resolve.get(), resolveMovingObjects.get(), apply.get()));
    }
}
