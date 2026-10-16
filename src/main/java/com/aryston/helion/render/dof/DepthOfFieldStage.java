package com.aryston.helion.render.dof;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.post.DepthOfFieldSettings;
import com.aryston.helion.render.shader.FullscreenPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class DepthOfFieldStage implements RenderStage {
    private static final String NAME = "depth_of_field";
    private static final String FOCUS_PASS = "dof_focus";
    private static final String DOWNSAMPLE_PASS = "dof_downsample";
    private static final String GATHER_PASS = "dof_gather";
    private static final String BLEND_PASS = "dof_blend";
    private static final String PREVIOUS_FOCUS = "helion:dof_focus_previous";
    private static final String NEXT_FOCUS = "helion:dof_focus_next";
    private static final String HALF_TARGET = "helion:dof_half";
    private static final String BLURRED_TARGET = "helion:dof_blurred";
    private static final int HALF_DIVISOR = 2;
    private static final int MIN_SIZE = 1;

    private final DepthOfFieldResources resources;
    private @Nullable DepthOfFieldPrograms programs;

    public DepthOfFieldStage(DepthOfFieldResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().image().depthOfField().enabled()
            || frame.scene().colorFormat() != DepthOfFieldPipelines.SCENE_COLOR_FORMAT) {
            resources.reset();
            return false;
        }
        Optional<DepthOfFieldPrograms> compiled = resources.programs();
        programs = compiled.orElse(null);
        return compiled.isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        DepthOfFieldPrograms compiled = Objects.requireNonNull(programs);
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        ResourceHandle<RenderTarget> focus = addFocusPass(frame, compiled, scene);
        ResourceHandle<RenderTarget> half = addDownsamplePass(frame, compiled, scene, focus);
        ResourceHandle<RenderTarget> blurred = addGatherPass(frame, compiled, half);
        addBlendPass(frame, compiled, blurred, focus, scene);
    }

    private ResourceHandle<RenderTarget> addFocusPass(FrameContext frame, DepthOfFieldPrograms compiled, ResourceHandle<RenderTarget> scene) {
        ResourceHandle<RenderTarget> previous = frame.graph().builder().importExternal(PREVIOUS_FOCUS, resources.previousFocus());
        ResourceHandle<RenderTarget> next = frame.graph().builder().importExternal(NEXT_FOCUS, resources.nextFocus());
        FramePass pass = frame.graph().addPass(FOCUS_PASS);
        pass.reads(scene);
        pass.reads(previous);
        ResourceHandle<RenderTarget> focus = pass.readsAndWrites(next);
        pass.executes(frame.graph().timed(FOCUS_PASS, () ->
            FullscreenPass.draw("Helion Depth Of Field Focus", focus.get(), compiled.focus(), renderPass -> {
                bindCommon(renderPass, frame);
                renderPass.setUniform(DepthOfFieldPipelines.DEPTH_SAMPLER, depthView(scene), nearest());
                renderPass.setUniform(DepthOfFieldPipelines.PREVIOUS_FOCUS_SAMPLER, colorView(previous), nearest());
            })));
        return focus;
    }

    private ResourceHandle<RenderTarget> addDownsamplePass(
        FrameContext frame,
        DepthOfFieldPrograms compiled,
        ResourceHandle<RenderTarget> scene,
        ResourceHandle<RenderTarget> focus
    ) {
        FramePass pass = frame.graph().addPass(DOWNSAMPLE_PASS);
        pass.reads(scene);
        pass.reads(focus);
        ResourceHandle<RenderTarget> half = pass.createsInternal(HALF_TARGET, halfTarget(frame));
        pass.executes(frame.graph().timed(DOWNSAMPLE_PASS, () ->
            FullscreenPass.draw("Helion Depth Of Field Downsample", half.get(), compiled.downsample(), renderPass -> {
                bindCommon(renderPass, frame);
                renderPass.setUniform(DepthOfFieldPipelines.SCENE_COLOR_SAMPLER, colorView(scene), nearest());
                renderPass.setUniform(DepthOfFieldPipelines.DEPTH_SAMPLER, depthView(scene), nearest());
                renderPass.setUniform(DepthOfFieldPipelines.FOCUS_SAMPLER, colorView(focus), nearest());
            })));
        return half;
    }

    private ResourceHandle<RenderTarget> addGatherPass(FrameContext frame, DepthOfFieldPrograms compiled, ResourceHandle<RenderTarget> half) {
        FramePass pass = frame.graph().addPass(GATHER_PASS);
        pass.reads(half);
        ResourceHandle<RenderTarget> blurred = pass.createsInternal(BLURRED_TARGET, halfTarget(frame));
        pass.executes(frame.graph().timed(GATHER_PASS, () ->
            FullscreenPass.draw("Helion Depth Of Field Gather", blurred.get(), compiled.gather(), renderPass -> {
                bindCommon(renderPass, frame);
                renderPass.setUniform(DepthOfFieldPipelines.HALF_SAMPLER, colorView(half), linear());
            })));
        return blurred;
    }

    private void addBlendPass(
        FrameContext frame,
        DepthOfFieldPrograms compiled,
        ResourceHandle<RenderTarget> blurred,
        ResourceHandle<RenderTarget> focus,
        ResourceHandle<RenderTarget> source
    ) {
        FramePass pass = frame.graph().addPass(BLEND_PASS);
        pass.reads(blurred);
        pass.reads(focus);
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(source);
        frame.targets().updateScene(scene);
        pass.executes(frame.graph().timed(BLEND_PASS, () -> {
            FullscreenPass.draw("Helion Depth Of Field Blend", scene.get(), compiled.blend(), renderPass -> {
                bindCommon(renderPass, frame);
                renderPass.setUniform(DepthOfFieldPipelines.BLURRED_SAMPLER, colorView(blurred), linear());
                renderPass.setUniform(DepthOfFieldPipelines.DEPTH_SAMPLER, depthView(scene), nearest());
                renderPass.setUniform(DepthOfFieldPipelines.FOCUS_SAMPLER, colorView(focus), nearest());
            });
            resources.finishFrame();
        }));
    }

    private void bindCommon(RenderPass renderPass, FrameContext frame) {
        DepthOfFieldSettings settings = frame.settings().image().depthOfField();
        GpuBuffer uniforms = resources.frameUniforms(settings, frame.scene().outputWidth(), frame.scene().outputHeight());
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform(DepthOfFieldPipelines.SETTINGS, uniforms);
    }

    private static RenderTargetDescriptor halfTarget(FrameContext frame) {
        return new RenderTargetDescriptor(
            Math.max(MIN_SIZE, frame.scene().outputWidth() / HALF_DIVISOR),
            Math.max(MIN_SIZE, frame.scene().outputHeight() / HALF_DIVISOR),
            new RenderTargetDescriptor.TextureProperties(null, DepthOfFieldPipelines.HALF_FORMAT),
            null
        );
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }

    private static GpuTextureView depthView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getDepthTextureView());
    }

    private static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    private static GpuSampler linear() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }
}
