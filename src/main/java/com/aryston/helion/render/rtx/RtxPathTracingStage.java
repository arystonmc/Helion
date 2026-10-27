package com.aryston.helion.render.rtx;

import com.aryston.helion.render.atmosphere.SkyLight;
import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.geometry.GeometryBufferPipelines;
import com.aryston.helion.render.geometry.GlossyMask;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.geometry.TerrainTextures;
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
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class RtxPathTracingStage implements RenderStage {
    static final String TRACE_PASS = "path_trace";
    private static final String NAME = "path_traced_light";
    private static final int MATRIX_BYTES = 16 * Float.BYTES;
    private static final long WHOLE_BUFFER = 0L;
    private static final float HORIZON = 0.0F;
    private static final float TRACED = 1.0F;
    private static final float UNUSED = 0.0F;
    private static final long TIME_PERIOD_NANOS = 3_600_000_000_000L;
    private static final double NANOS_PER_SECOND = 1.0e9;

    private final RayTracingResources resources;
    private final TerrainSource terrain;
    private final GlossyMask glossy;
    private @Nullable CompiledRenderPipeline radiance;
    private @Nullable RtxSectionPos anchor;
    private @Nullable SkyLight skyLight;

    public RtxPathTracingStage(RayTracingResources resources, TerrainSource terrain, GlossyMask glossy) {
        this.resources = resources;
        this.terrain = terrain;
        this.glossy = glossy;
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
        anchor = resources.scene().anchor();
        skyLight = frame.atmosphere().skyLight().orElse(null);
        Optional<CompiledRenderPipeline> compiled = HelionPipelines.compiled(PathTracingPipelines.RADIANCE);
        radiance = compiled.orElse(null);
        return compiled.isPresent() && anchor != null && skyLight != null;
    }

    @Override
    public void addTo(FrameContext frame) {
        GeometryBuffer.Targets geometry = frame.geometry().targets().orElseThrow();
        RtxImage image = resources.radiance(frame.scene().width(), frame.scene().height());
        trace(frame, geometry, image, Objects.requireNonNull(skyLight));
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

    private void trace(FrameContext frame, GeometryBuffer.Targets geometry, RtxImage image, SkyLight sky) {
        ByteBuffer constants = resources.pathTraceConstants();
        writeConstants(constants, frame.camera(), Objects.requireNonNull(anchor), sky);
        FramePass pass = frame.graph().addPass(TRACE_PASS);
        pass.reads(geometry.albedo());
        pass.reads(geometry.light());
        pass.reads(geometry.normal());
        pass.reads(sky.irradiance());
        ResourceHandle<RenderTarget> sceneTarget = frame.targets().scene();
        pass.reads(sceneTarget);
        pass.disableCulling();
        RtxDevice device = resources.device();
        RtxSectionScene scene = resources.scene();
        long topLevel = scene.topLevelHandle();
        RtxBuffer table = scene.surfaceTable();
        RtxBuffer lights = scene.lightBuffer();
        TerrainTextures textures = terrain.textures();
        pass.executes(frame.graph().timed(TRACE_PASS, () -> {
            device.memoryBarrier();
            resources.pathTracePipeline().dispatch(device.commandBuffer(), List.of(
                new RtxBinding.StorageImage(image.vkImageView()),
                new RtxBinding.Scene(topLevel),
                new RtxBinding.Sampled(handle(colorView(geometry.albedo()))),
                new RtxBinding.Sampled(handle(colorView(geometry.light()))),
                new RtxBinding.Sampled(handle(textures.lightmap())),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(sceneTarget.get().getDepthTextureView()))),
                new RtxBinding.Sampled(handle(colorView(geometry.normal()))),
                new RtxBinding.Sampled(handle(textures.atlas())),
                new RtxBinding.StorageBuffer(table.buffer(), WHOLE_BUFFER, table.size()),
                new RtxBinding.Sampled(handle(colorView(sky.irradiance()))),
                new RtxBinding.StorageBuffer(lights.buffer(), WHOLE_BUFFER, lights.size()),
                new RtxBinding.Sampled(handle(glossy.update(terrain.glossyCells())))
            ), constants, image.width(), image.height());
            device.memoryBarrier();
        }));
    }

    private static void writeConstants(ByteBuffer constants, HelionCamera camera, RtxSectionPos center, SkyLight sky) {
        Vector3dc position = camera.position();
        constants.clear();
        camera.levelViewProjection().invert().get(0, constants);
        constants.position(MATRIX_BYTES);
        constants.putFloat((float) (position.x() - center.originX()))
            .putFloat((float) (position.y() - center.originY()))
            .putFloat((float) (position.z() - center.originZ()))
            .putFloat(RtxSectionInbox.RADIUS * RtxSectionKey.SECTION_SIZE);
        putLight(constants, sky.sunDirection());
        putLight(constants, sky.moonDirection());
        long now = System.nanoTime();
        constants.putFloat((float) (Math.floorMod(now, TIME_PERIOD_NANOS) / NANOS_PER_SECOND)).putInt((int) now).putFloat(UNUSED).putFloat(UNUSED);
        constants.flip();
    }

    private static void putLight(ByteBuffer constants, Vector3fc direction) {
        constants.putFloat(direction.x()).putFloat(direction.y()).putFloat(direction.z()).putFloat(direction.y() > HORIZON ? TRACED : UNUSED);
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
