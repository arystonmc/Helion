package com.aryston.helion.render.voxel;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class VoxelProbeStage implements RenderStage {
    private static final String NAME = "voxel_probes";
    private static final String TARGET_PREFIX = "helion:voxel_probe_";
    private static final String PREVIOUS_PREFIX = "helion:voxel_probe_previous_";

    private final VoxelLightingResources resources;
    private @Nullable CompiledRenderPipeline pipeline;
    private @Nullable VoxelVolumeView volume;

    public VoxelProbeStage(VoxelLightingResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        VoxelLightingSettings voxels = frame.settings().voxels();
        if (!voxels.enabled() || !voxels.bounce() || !frame.settings().lighting().enabled() || frame.geometry().targets().isEmpty()) {
            return false;
        }
        Optional<VoxelVolumeView> view = resources.volume();
        Optional<CompiledRenderPipeline> compiled = HelionPipelines.compiled(VoxelLightPipelines.PROBE_UPDATE);
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
        VoxelProbes probes = resources.probes();
        VoxelLightingQuality quality = frame.settings().voxels().quality();
        VoxelLightingSettings voxelSettings = frame.settings().voxels();
        List<ResourceHandle<RenderTarget>> previous = imported(frame, PREVIOUS_PREFIX, probes.previousSet());
        List<ResourceHandle<RenderTarget>> imported = imported(frame, TARGET_PREFIX, probes.currentSet());
        FramePass pass = frame.graph().addPass(NAME);
        previous.forEach(pass::reads);
        List<ResourceHandle<RenderTarget>> current = imported.stream().map(pass::readsAndWrites).toList();
        frame.voxels().publishProbes(current);
        HelionCamera camera = frame.camera();
        pass.executes(frame.graph().timed(NAME, () -> {
            GpuBuffer light = resources.writeLightUniforms(camera, view, voxelSettings);
            GpuBuffer settings = probes.writeUniforms(view, quality.probeRays());
            List<RenderTarget> written = current.stream().map(ResourceHandle::get).toList();
            FullscreenPass.draw("Helion Voxel Probes", written, compiled, renderPass -> {
                renderPass.setUniform(VoxelLightPipelines.SETTINGS, light);
                renderPass.setUniform(VoxelLightPipelines.PROBE, settings);
                renderPass.setUniform(VoxelLightPipelines.VOXEL_SAMPLER, view.texture(), nearest());
                renderPass.setUniform(VoxelLightPipelines.COARSE_SAMPLER, view.coarse(), nearest());
                renderPass.setUniform(VoxelLightPipelines.COLOR_SAMPLER, view.colors(), nearest());
                renderPass.setUniform(VoxelLightPipelines.READY_SAMPLER, view.ready(), nearest());
                renderPass.setUniform(VoxelLightPipelines.LIGHT_LIST_SAMPLER, view.lights(), nearest());
                renderPass.setUniform(VoxelLightPipelines.LIGHT_GRID_SAMPLER, view.grid(), nearest());
                for (int channel = 0; channel < previous.size(); channel++) {
                    renderPass.setUniform(
                        VoxelLightPipelines.PREVIOUS_PROBE_SAMPLERS.get(channel),
                        Objects.requireNonNull(previous.get(channel).get().getColorTextureView()),
                        nearest()
                    );
                }
            });
            probes.finishFrame(view);
        }));
    }

    private static List<ResourceHandle<RenderTarget>> imported(FrameContext frame, String prefix, List<TextureTarget> targets) {
        List<ResourceHandle<RenderTarget>> handles = new ArrayList<>(targets.size());
        for (int channel = 0; channel < targets.size(); channel++) {
            handles.add(frame.graph().builder().importExternal(prefix + channel, targets.get(channel)));
        }
        return handles;
    }

    private static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }
}
