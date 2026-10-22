package com.aryston.helion.render.voxel;

import com.aryston.helion.render.geometry.GeometryBufferView;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class VoxelSliceStage implements RenderStage {
    private static final String NAME = "voxel_slice";

    private final VoxelLightingResources resources;
    private @Nullable CompiledRenderPipeline pipeline;
    private @Nullable VoxelVolumeView volume;

    public VoxelSliceStage(VoxelLightingResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().geometry().showsView() || frame.settings().geometry().view() != GeometryBufferView.VOXEL_SLICE
            || frame.scene().colorFormat() != VoxelSlicePipelines.OUTPUT_FORMAT) {
            return false;
        }
        Optional<VoxelVolumeView> view = resources.volume();
        Optional<CompiledRenderPipeline> compiled = HelionPipelines.compiled(VoxelSlicePipelines.SLICE);
        volume = view.orElse(null);
        pipeline = compiled.orElse(null);
        return view.isPresent() && compiled.isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        CompiledRenderPipeline compiled = Objects.requireNonNull(pipeline);
        VoxelVolumeView view = Objects.requireNonNull(volume);
        GpuBuffer uniforms = resources.writeSliceUniforms(view, frame.scene().outputWidth(), frame.scene().outputHeight());
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> output = pass.readsAndWrites(frame.targets().output());
        frame.targets().updateOutput(output);
        pass.executes(frame.graph().timed(NAME, () -> {
            FullscreenPass.draw("Helion Voxel Slice", output.get(), compiled, renderPass -> {
                renderPass.setUniform(VoxelSlicePipelines.SETTINGS, uniforms);
                renderPass.setUniform(VoxelSlicePipelines.VOXEL_SAMPLER, view.texture(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            });
            resources.finishFrame();
        }));
    }
}
