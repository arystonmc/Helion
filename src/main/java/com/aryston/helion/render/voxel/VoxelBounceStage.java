package com.aryston.helion.render.voxel;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class VoxelBounceStage implements RenderStage {
    private static final String NAME = "voxel_bounce";
    private static final String TARGET = "helion:voxel_bounce";
    private static final int RESOLUTION_DIVISOR = 2;

    private final VoxelLightingResources resources;
    private @Nullable CompiledRenderPipeline pipeline;
    private @Nullable VoxelVolumeView volume;

    public VoxelBounceStage(VoxelLightingResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().voxels().enabled() || !frame.settings().lighting().enabled() || frame.geometry().targets().isEmpty()) {
            return false;
        }
        Optional<VoxelVolumeView> view = resources.volume();
        Optional<CompiledRenderPipeline> compiled = HelionPipelines.compiled(VoxelLightPipelines.BOUNCE);
        if (compiled.isEmpty()) {
            resources.reportMissingPipelines();
        }
        volume = view.orElse(null);
        pipeline = compiled.orElse(null);
        return view.isPresent() && compiled.isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        CompiledRenderPipeline compiled = Objects.requireNonNull(pipeline);
        VoxelVolumeView view = Objects.requireNonNull(volume);
        GeometryBuffer.Targets geometry = frame.geometry().targets().orElseThrow();
        int width = Math.ceilDiv(frame.scene().width(), RESOLUTION_DIVISOR);
        int height = Math.ceilDiv(frame.scene().height(), RESOLUTION_DIVISOR);
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(geometry.normal());
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        pass.reads(scene);
        List<ResourceHandle<RenderTarget>> probes = frame.voxels().probes();
        probes.forEach(pass::reads);
        ResourceHandle<RenderTarget> bounce = pass.createsInternal(TARGET, new RenderTargetDescriptor(
            width, height, new RenderTargetDescriptor.TextureProperties(null, VoxelLightPipelines.BOUNCE_FORMAT), null
        ));
        frame.voxels().publishBounce(bounce);
        HelionCamera camera = frame.camera();
        VoxelLightingSettings settings = frame.settings().voxels();
        pass.executes(frame.graph().timed(NAME, () -> {
            GpuBuffer uniforms = resources.writeLightUniforms(camera, view, settings);
            GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            FullscreenPass.draw("Helion Voxel Bounce", bounce.get(), compiled, renderPass -> {
                renderPass.setUniform(VoxelLightPipelines.SETTINGS, uniforms);
                renderPass.setUniform(VoxelLightPipelines.DEPTH_SAMPLER, Objects.requireNonNull(scene.get().getDepthTextureView()), nearest);
                renderPass.setUniform(VoxelLightPipelines.GEOMETRY_NORMAL_SAMPLER, colorView(geometry.normal()), nearest);
                renderPass.setUniform(VoxelLightPipelines.VOXEL_SAMPLER, view.texture(), nearest);
                renderPass.setUniform(VoxelLightPipelines.COARSE_SAMPLER, view.coarse(), nearest);
                renderPass.setUniform(VoxelLightPipelines.READY_SAMPLER, view.ready(), nearest);
                renderPass.setUniform(VoxelLightPipelines.LIGHT_GRID_SAMPLER, view.grid(), nearest);
                for (int channel = 0; channel < VoxelLightPipelines.PROBE_SAMPLERS.size(); channel++) {
                    GpuTextureView probe = probes.isEmpty() ? view.colors() : colorView(probes.get(channel));
                    renderPass.setUniform(VoxelLightPipelines.PROBE_SAMPLERS.get(channel), probe, nearest);
                }
            });
        }));
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }
}
