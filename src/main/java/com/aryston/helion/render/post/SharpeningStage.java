package com.aryston.helion.render.post;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class SharpeningStage implements RenderStage {
    private static final String NAME = "sharpen";
    private static final String TARGET = "helion:sharpened";

    private final ImageResources resources;
    private @Nullable ImagePrograms programs;

    public SharpeningStage(ImageResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().image().sharpening().enabled()) {
            return false;
        }
        Optional<ImagePrograms> compiled = resources.programs(frame);
        programs = compiled.orElse(null);
        return compiled.isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        ImagePrograms compiled = Objects.requireNonNull(programs);
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        pass.reads(scene);
        ResourceHandle<RenderTarget> sharpened = pass.createsInternal(TARGET, new RenderTargetDescriptor(
            frame.scene().width(),
            frame.scene().height(),
            new RenderTargetDescriptor.TextureProperties(null, ImagePipelines.OUTPUT_FORMAT),
            null
        ));
        pass.executes(frame.graph().timed(NAME, () -> {
            GpuBuffer uniforms = resources.frameUniforms(frame);
            FullscreenPass.draw("Helion Sharpen", sharpened.get(), compiled.sharpen(), renderPass -> {
                renderPass.setUniform(ImagePipelines.SETTINGS, uniforms);
                renderPass.setUniform(ImagePipelines.SCENE_COLOR_SAMPLER, PostSampling.colorView(scene), PostSampling.nearest());
            });
        }));
        frame.post().publishSharpened(sharpened);
    }
}
