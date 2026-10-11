package com.aryston.helion.render.post;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.Optional;

record ExposurePrograms(CompiledRenderPipeline meter, CompiledRenderPipeline average) {
    static Optional<ExposurePrograms> compile() {
        Optional<CompiledRenderPipeline> meter = HelionPipelines.compiled(ImagePipelines.EXPOSURE_METER);
        Optional<CompiledRenderPipeline> average = HelionPipelines.compiled(ImagePipelines.EXPOSURE_AVERAGE);
        if (meter.isEmpty() || average.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ExposurePrograms(meter.get(), average.get()));
    }
}
