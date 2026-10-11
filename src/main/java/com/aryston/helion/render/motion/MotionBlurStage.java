package com.aryston.helion.render.motion;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class MotionBlurStage implements RenderStage {
    private static final String NAME = "motion_blur";
    private static final String TILE_MAX_PASS = "motion_blur_tiles";
    private static final String NEIGHBOR_MAX_PASS = "motion_blur_neighbors";
    private static final String GATHER_PASS = "motion_blur_gather";
    private static final String APPLY_PASS = "motion_blur_apply";
    private static final String TILE_MAX_TARGET = "helion:motion_tile_max";
    private static final String NEIGHBOR_MAX_TARGET = "helion:motion_neighbor_max";
    private static final String BLURRED_TARGET = "helion:motion_blurred";

    private final MotionBlurResources resources;
    private @Nullable MotionBlurPrograms programs;

    public MotionBlurStage(MotionBlurResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().image().motionBlur().enabled()
            || !frame.temporal().cameraMotionValid()
            || frame.scene().colorFormat() != MotionBlurPipelines.SCENE_COLOR_FORMAT) {
            return false;
        }
        Optional<MotionBlurPrograms> compiled = resources.programs();
        programs = compiled.orElse(null);
        return compiled.isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        MotionBlurPrograms compiled = Objects.requireNonNull(programs);
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        ResourceHandle<RenderTarget> tileMax = addTileMaxPass(frame, compiled, scene);
        ResourceHandle<RenderTarget> neighborMax = addNeighborMaxPass(frame, compiled, tileMax);
        ResourceHandle<RenderTarget> blurred = addGatherPass(frame, compiled, scene, neighborMax);
        addApplyPass(frame, compiled, blurred);
    }

    private ResourceHandle<RenderTarget> addTileMaxPass(
        FrameContext frame,
        MotionBlurPrograms compiled,
        ResourceHandle<RenderTarget> scene
    ) {
        FramePass pass = frame.graph().addPass(TILE_MAX_PASS);
        pass.reads(scene);
        ResourceHandle<RenderTarget> tileMax = pass.createsInternal(TILE_MAX_TARGET, tileTarget(frame));
        pass.executes(frame.graph().timed(TILE_MAX_PASS, () -> FullscreenPass.draw(
            "Helion Motion Blur Tiles", tileMax.get(), compiled.tileMax(), renderPass -> {
                renderPass.setUniform(MotionBlurPipelines.SETTINGS, uniforms(frame));
                renderPass.setUniform(MotionBlurPipelines.DEPTH_SAMPLER, depthView(scene), nearest());
            })));
        return tileMax;
    }

    private ResourceHandle<RenderTarget> addNeighborMaxPass(FrameContext frame, MotionBlurPrograms compiled, ResourceHandle<RenderTarget> tileMax) {
        FramePass pass = frame.graph().addPass(NEIGHBOR_MAX_PASS);
        pass.reads(tileMax);
        ResourceHandle<RenderTarget> neighborMax = pass.createsInternal(NEIGHBOR_MAX_TARGET, tileTarget(frame));
        pass.executes(frame.graph().timed(NEIGHBOR_MAX_PASS, () -> FullscreenPass.draw(
            "Helion Motion Blur Neighbors", neighborMax.get(), compiled.neighborMax(), renderPass ->
                renderPass.setUniform(MotionBlurPipelines.TILE_MAX_SAMPLER, colorView(tileMax), nearest()))));
        return neighborMax;
    }

    private ResourceHandle<RenderTarget> addGatherPass(
        FrameContext frame,
        MotionBlurPrograms compiled,
        ResourceHandle<RenderTarget> scene,
        ResourceHandle<RenderTarget> neighborMax
    ) {
        FramePass pass = frame.graph().addPass(GATHER_PASS);
        pass.reads(scene);
        pass.reads(neighborMax);
        ResourceHandle<RenderTarget> blurred = pass.createsInternal(
            BLURRED_TARGET, target(frame.scene().outputWidth(), frame.scene().outputHeight(), MotionBlurPipelines.SCENE_COLOR_FORMAT)
        );
        pass.executes(frame.graph().timed(GATHER_PASS, () -> FullscreenPass.draw(
            "Helion Motion Blur Gather", blurred.get(), compiled.gather(), renderPass -> {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform(MotionBlurPipelines.SETTINGS, uniforms(frame));
                renderPass.setUniform(MotionBlurPipelines.SCENE_COLOR_SAMPLER, colorView(scene), nearest());
                renderPass.setUniform(MotionBlurPipelines.DEPTH_SAMPLER, depthView(scene), nearest());
                renderPass.setUniform(MotionBlurPipelines.NEIGHBOR_MAX_SAMPLER, colorView(neighborMax), nearest());
            })));
        return blurred;
    }

    private void addApplyPass(FrameContext frame, MotionBlurPrograms compiled, ResourceHandle<RenderTarget> blurred) {
        FramePass pass = frame.graph().addPass(APPLY_PASS);
        pass.reads(blurred);
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        pass.executes(frame.graph().timed(APPLY_PASS, () -> {
            FullscreenPass.draw("Helion Motion Blur Apply", scene.get(), compiled.apply(), renderPass ->
                renderPass.setUniform(MotionBlurPipelines.BLURRED_SAMPLER, colorView(blurred), nearest()));
            resources.finishFrame();
        }));
    }

    private GpuBuffer uniforms(FrameContext frame) {
        return resources.frameUniforms(
            frame.temporal().reprojection(), frame.settings().image().motionBlur(), frame.scene().outputWidth(), frame.scene().outputHeight()
        );
    }

    private static RenderTargetDescriptor tileTarget(FrameContext frame) {
        return target(tiles(frame.scene().outputWidth()), tiles(frame.scene().outputHeight()), MotionBlurPipelines.VELOCITY_FORMAT);
    }

    private static int tiles(int pixels) {
        return Math.ceilDiv(pixels, MotionBlurPipelines.TILE_SIZE);
    }

    private static RenderTargetDescriptor target(int width, int height, GpuFormat format) {
        return new RenderTargetDescriptor(width, height, new RenderTargetDescriptor.TextureProperties(null, format), null);
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
}
