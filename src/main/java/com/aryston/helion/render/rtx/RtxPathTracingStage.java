package com.aryston.helion.render.rtx;

import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.geometry.GeometryBufferPipelines;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.pathtracing.PathTracingPipelines;
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
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class RtxPathTracingStage implements RenderStage {
    static final String TRACE_PASS = "path_trace";
    private static final String NAME = "path_traced_light";

    private final RayTracingResources resources;
    private final TerrainSource terrain;
    private @Nullable CompiledRenderPipeline radiance;

    public RtxPathTracingStage(RayTracingResources resources, TerrainSource terrain) {
        this.resources = resources;
        this.terrain = terrain;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().rayTracing().tracesLight() || frame.geometry().targets().isEmpty()
            || frame.scene().colorFormat() != GeometryBufferPipelines.OUTPUT_FORMAT || !resources.available() || !resources.pathTracingAvailable()) {
            return false;
        }
        Optional<CompiledRenderPipeline> compiled = HelionPipelines.compiled(PathTracingPipelines.RADIANCE);
        radiance = compiled.orElse(null);
        return compiled.isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        GeometryBuffer.Targets geometry = frame.geometry().targets().orElseThrow();
        RtxImage image = resources.radiance(frame.scene().width(), frame.scene().height());
        trace(frame, geometry, image);
        CompiledRenderPipeline pipeline = Objects.requireNonNull(radiance);
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(geometry.albedo());
        pass.reads(geometry.light());
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        pass.executes(frame.graph().timed(NAME, () -> FullscreenPass.draw("Helion Path Traced Light", scene.get(), pipeline, renderPass -> {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform(PathTracingPipelines.RADIANCE_SAMPLER, image.view(), nearest());
            renderPass.setUniform(PathTracingPipelines.ALBEDO_SAMPLER, colorView(geometry.albedo()), nearest());
            renderPass.setUniform(PathTracingPipelines.GEOMETRY_LIGHT_SAMPLER, colorView(geometry.light()), nearest());
        })));
    }

    private void trace(FrameContext frame, GeometryBuffer.Targets geometry, RtxImage image) {
        FramePass pass = frame.graph().addPass(TRACE_PASS);
        pass.reads(geometry.albedo());
        pass.reads(geometry.light());
        pass.disableCulling();
        RtxDevice device = resources.device();
        long lightmap = handle(terrain.textures().lightmap());
        pass.executes(frame.graph().timed(TRACE_PASS, () -> {
            device.memoryBarrier();
            resources.pathTracePipeline().dispatch(device.commandBuffer(), List.of(
                new RtxBinding.StorageImage(image.vkImageView()),
                new RtxBinding.Sampled(handle(colorView(geometry.albedo()))),
                new RtxBinding.Sampled(handle(colorView(geometry.light()))),
                new RtxBinding.Sampled(lightmap)
            ), image.width(), image.height());
            device.memoryBarrier();
        }));
    }

    private static long handle(GpuTextureView view) {
        return ((VulkanGpuTextureView) view).vkImageView();
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }

    private static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }
}
