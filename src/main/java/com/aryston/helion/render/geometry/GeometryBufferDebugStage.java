package com.aryston.helion.render.geometry;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class GeometryBufferDebugStage implements RenderStage {
    private static final String NAME = "geometry_view";

    private @Nullable CompiledRenderPipeline pipeline;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        GeometryBufferSettings settings = frame.settings().geometry();
        if (!settings.showsView() || frame.geometry().targets().isEmpty()
            || frame.scene().colorFormat() != GeometryBufferPipelines.OUTPUT_FORMAT) {
            return false;
        }
        Optional<CompiledRenderPipeline> compiled = GeometryBufferPipelines.debugView(settings.view()).flatMap(HelionPipelines::compiled);
        pipeline = compiled.orElse(null);
        return compiled.isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        CompiledRenderPipeline compiled = Objects.requireNonNull(pipeline);
        GeometryBuffer.Targets targets = frame.geometry().targets().orElseThrow();
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(targets.normal());
        pass.reads(targets.light());
        pass.reads(targets.albedo());
        ResourceHandle<RenderTarget> output = pass.readsAndWrites(frame.targets().output());
        frame.targets().updateOutput(output);
        pass.executes(frame.graph().timed(NAME, () -> FullscreenPass.draw("Helion Geometry Buffer View", output.get(), compiled, renderPass -> {
            renderPass.setUniform(GeometryBufferPipelines.NORMAL_SAMPLER, colorView(targets.normal()), nearest());
            renderPass.setUniform(GeometryBufferPipelines.LIGHT_SAMPLER, colorView(targets.light()), nearest());
            renderPass.setUniform(GeometryBufferPipelines.ALBEDO_SAMPLER, colorView(targets.albedo()), nearest());
        })));
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }

    private static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }
}
