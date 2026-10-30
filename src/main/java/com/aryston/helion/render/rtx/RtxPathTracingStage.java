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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class RtxPathTracingStage implements RenderStage {
    static final String TRACE_PASS = "path_trace";
    static final String DENOISE_PASS = "path_denoise";
    private static final String NAME = "path_traced_light";
    private static final int MATRIX_BYTES = 16 * Float.BYTES;
    private static final long WHOLE_BUFFER = 0L;
    private static final float HORIZON = 0.0F;
    private static final float TRACED = 1.0F;
    private static final float UNUSED = 0.0F;
    private static final long TIME_PERIOD_NANOS = 3_600_000_000_000L;
    private static final double NANOS_PER_SECOND = 1.0e9;
    private static final int TRACED_SLOT = 0;
    private static final int FINAL_SLOT = 1;
    private static final int UNUSED_STEP = 0;
    private static final int[] FILTER_STEPS = {1, 2, 4};

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
            resources.recordPathTraced(false);
            return false;
        }
        anchor = resources.scene().anchor();
        skyLight = frame.atmosphere().skyLight().orElse(null);
        Optional<CompiledRenderPipeline> compiled = HelionPipelines.compiled(PathTracingPipelines.RADIANCE);
        radiance = compiled.orElse(null);
        boolean traced = compiled.isPresent() && anchor != null && skyLight != null;
        resources.recordPathTraced(traced);
        return traced;
    }

    @Override
    public void addTo(FrameContext frame) {
        GeometryBuffer.Targets geometry = frame.geometry().targets().orElseThrow();
        PathTracingQuality quality = frame.settings().rayTracing().pathTracingQuality();
        RtxDenoiseImages images = resources.denoiseImages(frame.scene().width(), frame.scene().height(), quality.resolutionDivisor());
        trace(frame, geometry, images.work(TRACED_SLOT), images.specular(), Objects.requireNonNull(skyLight), quality);
        denoise(frame, geometry, images);
        frame.geometry().publishTracedSpecular();
        RtxImage image = images.upsampled().orElse(images.work(FINAL_SLOT));
        RtxImage specular = images.upsampledSpecular().orElse(images.specularHistory());
        CompiledRenderPipeline pipeline = Objects.requireNonNull(radiance);
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(geometry.albedo());
        pass.reads(geometry.light());
        pass.reads(geometry.normal());
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        pass.executes(frame.graph().timed(NAME, () -> FullscreenPass.draw("Helion Path Traced Light", scene.get(), pipeline, renderPass -> {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform(PathTracingPipelines.IRRADIANCE_SAMPLER, image.view(), nearest());
            renderPass.setUniform(PathTracingPipelines.SPECULAR_SAMPLER, specular.view(), nearest());
            renderPass.setUniform(PathTracingPipelines.ALBEDO_SAMPLER, colorView(geometry.albedo()), nearest());
            renderPass.setUniform(PathTracingPipelines.GEOMETRY_LIGHT_SAMPLER, colorView(geometry.light()), nearest());
            renderPass.setUniform(PathTracingPipelines.GEOMETRY_NORMAL_SAMPLER, colorView(geometry.normal()), nearest());
        })));
    }

    private void trace(FrameContext frame, GeometryBuffer.Targets geometry, RtxImage image, RtxImage specular, SkyLight sky, PathTracingQuality quality) {
        ByteBuffer constants = resources.pathTraceConstants();
        writeConstants(constants, frame.camera(), Objects.requireNonNull(anchor), sky, resources.nextPathTraceFrame());
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
        RtxLightCache cache = resources.lightCache();
        List<RtxBinding> cacheTrace = List.of(cache.entries(), cache.filling(), cache.queued(), new RtxBinding.StorageImage(specular.vkImageView()));
        List<RtxBinding> cacheUpdate = List.of(cache.entries(), cache.queued(), cache.filling(), new RtxBinding.StorageImage(specular.vkImageView()));
        cache.advance();
        pass.executes(frame.graph().timed(TRACE_PASS, () -> {
            List<RtxBinding> bindings = List.of(
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
            );
            device.memoryBarrier();
            if (cache.clearIfNew(device.commandBuffer())) {
                device.memoryBarrier();
            }
            resources.cacheUpdatePipeline(quality).dispatch(
                device.commandBuffer(), concat(bindings, cacheUpdate), constants, RtxLightCache.QUEUE_WIDTH, quality.cacheRows()
            );
            device.memoryBarrier();
            resources.pathTracePipeline(quality).dispatch(device.commandBuffer(), concat(bindings, cacheTrace), constants, image.width(), image.height());
            device.memoryBarrier();
        }));
    }

    private void denoise(FrameContext frame, GeometryBuffer.Targets geometry, RtxDenoiseImages images) {
        ByteBuffer constants = resources.accumulateConstants();
        writeDenoiseConstants(constants, frame.camera(), images);
        FramePass pass = frame.graph().addPass(DENOISE_PASS);
        pass.reads(geometry.normal());
        pass.reads(geometry.light());
        ResourceHandle<RenderTarget> sceneTarget = frame.targets().scene();
        pass.reads(sceneTarget);
        pass.disableCulling();
        DenoiseFrame targets = DenoiseFrame.of(images, geometry, sceneTarget, constants);
        pass.executes(frame.graph().timed(DENOISE_PASS, () -> runDenoise(targets)));
    }

    private static void writeDenoiseConstants(ByteBuffer constants, HelionCamera camera, RtxDenoiseImages images) {
        constants.clear();
        camera.levelViewProjection().invert().get(0, constants);
        images.writeReprojection(camera, MATRIX_BYTES, constants);
        images.advance(camera.levelViewProjection(), camera.position());
    }

    private void runDenoise(DenoiseFrame frame) {
        accumulate(frame);
        filter(frame);
        resources.device().memoryBarrier();
        frame.images().upsampled().ifPresent(upsampled -> upsample(frame, upsampled));
    }

    private void accumulate(DenoiseFrame frame) {
        RtxDevice device = resources.device();
        RtxDenoiseImages images = frame.images();
        resources.accumulatePipeline().dispatch(device.commandBuffer(), List.of(
            new RtxBinding.StorageImage(frame.accumulated().vkImageView()),
            new RtxBinding.StorageImage(frame.moments().vkImageView()),
            new RtxBinding.StorageImage(frame.guide().vkImageView()),
            new RtxBinding.Sampled(frame.traced().vkImageView()),
            new RtxBinding.Sampled(frame.history().vkImageView()),
            new RtxBinding.Sampled(frame.previousMoments().vkImageView()),
            new RtxBinding.Sampled(frame.previousGuide().vkImageView()),
            new RtxBinding.Sampled(frame.depth()),
            new RtxBinding.Sampled(frame.normal())
        ), frame.constants(), images.width(), images.height());
        resources.specularPipeline().dispatch(device.commandBuffer(), List.of(
            new RtxBinding.StorageImage(frame.specularHistory().vkImageView()),
            new RtxBinding.Sampled(frame.specular().vkImageView()),
            new RtxBinding.Sampled(frame.previousSpecularHistory().vkImageView()),
            new RtxBinding.Sampled(frame.previousGuide().vkImageView()),
            new RtxBinding.Sampled(frame.depth()),
            new RtxBinding.Sampled(frame.normal())
        ), frame.constants(), images.width(), images.height());
    }

    private void filter(DenoiseFrame frame) {
        RtxDevice device = resources.device();
        ByteBuffer steps = resources.filterConstants();
        RtxImage[] chain = {frame.accumulated(), frame.history(), frame.traced(), frame.accumulated()};
        for (int iteration = 0; iteration < FILTER_STEPS.length; iteration++) {
            device.memoryBarrier();
            steps.clear();
            steps.putInt(FILTER_STEPS[iteration]).putInt(UNUSED_STEP).putInt(UNUSED_STEP).putInt(UNUSED_STEP).flip();
            resources.filterPipeline().dispatch(device.commandBuffer(), List.of(
                new RtxBinding.StorageImage(chain[iteration + 1].vkImageView()),
                new RtxBinding.Sampled(chain[iteration].vkImageView()),
                new RtxBinding.Sampled(frame.guide().vkImageView())
            ), steps, frame.images().width(), frame.images().height());
        }
    }

    private void upsample(DenoiseFrame frame, RtxImage upsampled) {
        RtxDevice device = resources.device();
        resources.upsamplePipeline().dispatch(device.commandBuffer(), List.of(
            new RtxBinding.StorageImage(upsampled.vkImageView()),
            new RtxBinding.StorageImage(frame.images().upsampledSpecular().orElseThrow().vkImageView()),
            new RtxBinding.Sampled(frame.accumulated().vkImageView()),
            new RtxBinding.Sampled(frame.specularHistory().vkImageView()),
            new RtxBinding.Sampled(frame.guide().vkImageView()),
            new RtxBinding.Sampled(frame.depth()),
            new RtxBinding.Sampled(frame.normal()),
            new RtxBinding.Sampled(handle(colorView(frame.geometry().light())))
        ), frame.constants(), upsampled.width(), upsampled.height());
        device.memoryBarrier();
    }

    private static void writeConstants(ByteBuffer constants, HelionCamera camera, RtxSectionPos center, SkyLight sky, int frameCount) {
        Vector3dc position = camera.position();
        constants.clear();
        camera.levelViewProjection().invert().get(0, constants);
        constants.position(MATRIX_BYTES);
        constants.putFloat((float) (position.x() - center.originX()))
            .putFloat((float) (position.y() - center.originY()))
            .putFloat((float) (position.z() - center.originZ()))
            .putFloat((float) (Math.floorMod(System.nanoTime(), TIME_PERIOD_NANOS) / NANOS_PER_SECOND));
        putLight(constants, sky.sunDirection());
        putLight(constants, sky.moonDirection());
        constants.putInt(center.sectionX()).putInt(center.sectionY()).putInt(center.sectionZ()).putInt(frameCount);
        constants.flip();
    }

    private static void putLight(ByteBuffer constants, Vector3fc direction) {
        constants.putFloat(direction.x()).putFloat(direction.y()).putFloat(direction.z()).putFloat(direction.y() > HORIZON ? TRACED : UNUSED);
    }

    private static List<RtxBinding> concat(List<RtxBinding> first, List<RtxBinding> second) {
        List<RtxBinding> joined = new ArrayList<>(first);
        joined.addAll(second);
        return joined;
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

    private record DenoiseFrame(
        RtxDenoiseImages images,
        GeometryBuffer.Targets geometry,
        ResourceHandle<RenderTarget> sceneTarget,
        ByteBuffer constants,
        RtxImage traced,
        RtxImage accumulated,
        RtxImage history,
        RtxImage moments,
        RtxImage previousMoments,
        RtxImage guide,
        RtxImage previousGuide,
        RtxImage specular,
        RtxImage specularHistory,
        RtxImage previousSpecularHistory
    ) {
        static DenoiseFrame of(RtxDenoiseImages images, GeometryBuffer.Targets geometry, ResourceHandle<RenderTarget> sceneTarget, ByteBuffer constants) {
            return new DenoiseFrame(
                images, geometry, sceneTarget, constants,
                images.work(TRACED_SLOT), images.work(FINAL_SLOT), images.history(),
                images.moments(), images.previousMoments(),
                images.guide(), images.previousGuide(),
                images.specular(), images.specularHistory(), images.previousSpecularHistory()
            );
        }

        long depth() {
            return handle(Objects.requireNonNull(sceneTarget.get().getDepthTextureView()));
        }

        long normal() {
            return handle(colorView(geometry.normal()));
        }
    }
}
