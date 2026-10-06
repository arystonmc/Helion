package com.aryston.helion.render.post;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.Optional;

record ImagePrograms(
    CompiledRenderPipeline bloomPrefilter,
    CompiledRenderPipeline bloomDownsample,
    CompiledRenderPipeline bloomUpsample,
    CompiledRenderPipeline composite,
    CompiledRenderPipeline bloomDebug,
    CompiledRenderPipeline sharpen
) {
    static Optional<ImagePrograms> compile() {
        Optional<CompiledRenderPipeline> bloomPrefilter = HelionPipelines.compiled(ImagePipelines.BLOOM_PREFILTER);
        Optional<CompiledRenderPipeline> bloomDownsample = HelionPipelines.compiled(ImagePipelines.BLOOM_DOWNSAMPLE);
        Optional<CompiledRenderPipeline> bloomUpsample = HelionPipelines.compiled(ImagePipelines.BLOOM_UPSAMPLE);
        Optional<CompiledRenderPipeline> composite = HelionPipelines.compiled(ImagePipelines.COMPOSITE);
        Optional<CompiledRenderPipeline> bloomDebug = HelionPipelines.compiled(ImagePipelines.BLOOM_DEBUG);
        Optional<CompiledRenderPipeline> sharpen = HelionPipelines.compiled(ImagePipelines.SHARPEN);
        if (bloomPrefilter.isEmpty() || bloomDownsample.isEmpty() || bloomUpsample.isEmpty()
            || composite.isEmpty() || bloomDebug.isEmpty() || sharpen.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ImagePrograms(
            bloomPrefilter.get(), bloomDownsample.get(), bloomUpsample.get(), composite.get(), bloomDebug.get(), sharpen.get()
        ));
    }
}
