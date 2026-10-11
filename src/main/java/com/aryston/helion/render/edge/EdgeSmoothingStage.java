package com.aryston.helion.render.edge;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class EdgeSmoothingStage implements RenderStage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "edge_smoothing";
    private static final String SMOOTH_PASS = "edge_smoothing";
    private static final String APPLY_PASS = "edge_smoothing_apply";
    private static final String SMOOTHED_TARGET = "helion:edge_smoothed";
    private static boolean missingPipelinesReported;

    private @Nullable CompiledRenderPipeline smooth;
    private @Nullable CompiledRenderPipeline apply;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().image().edgeSmoothing()
            || frame.temporal().active()
            || frame.scene().colorFormat() != EdgeSmoothingPipelines.SCENE_COLOR_FORMAT) {
            return false;
        }
        Optional<CompiledRenderPipeline> compiledSmooth = HelionPipelines.compiled(EdgeSmoothingPipelines.SMOOTH);
        Optional<CompiledRenderPipeline> compiledApply = HelionPipelines.compiled(EdgeSmoothingPipelines.APPLY);
        if (compiledSmooth.isEmpty() || compiledApply.isEmpty()) {
            reportMissingPipelines();
            return false;
        }
        smooth = compiledSmooth.get();
        apply = compiledApply.get();
        return true;
    }

    @Override
    public void addTo(FrameContext frame) {
        CompiledRenderPipeline smoothPipeline = Objects.requireNonNull(smooth);
        CompiledRenderPipeline applyPipeline = Objects.requireNonNull(apply);
        FramePass smoothPass = frame.graph().addPass(SMOOTH_PASS);
        ResourceHandle<RenderTarget> source = frame.targets().scene();
        smoothPass.reads(source);
        ResourceHandle<RenderTarget> smoothed = smoothPass.createsInternal(SMOOTHED_TARGET, new RenderTargetDescriptor(
            frame.scene().outputWidth(),
            frame.scene().outputHeight(),
            new RenderTargetDescriptor.TextureProperties(null, EdgeSmoothingPipelines.SCENE_COLOR_FORMAT),
            null
        ));
        smoothPass.executes(frame.graph().timed(SMOOTH_PASS, () -> FullscreenPass.draw("Helion Edge Smoothing", smoothed.get(), smoothPipeline, renderPass ->
            renderPass.setUniform(EdgeSmoothingPipelines.SCENE_COLOR_SAMPLER, colorView(source), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)))));
        FramePass applyPass = frame.graph().addPass(APPLY_PASS);
        applyPass.reads(smoothed);
        ResourceHandle<RenderTarget> scene = applyPass.readsAndWrites(source);
        frame.targets().updateScene(scene);
        applyPass.executes(frame.graph().timed(APPLY_PASS, () -> FullscreenPass.draw("Helion Edge Smoothing Apply", scene.get(), applyPipeline, renderPass ->
            renderPass.setUniform(EdgeSmoothingPipelines.SMOOTHED_SAMPLER, colorView(smoothed), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)))));
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }

    private static void reportMissingPipelines() {
        if (!missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion edge smoothing is skipped because its shaders could not be compiled");
        }
    }
}
