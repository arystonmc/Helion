package com.aryston.helion.render.rtx;

import com.aryston.helion.render.atmosphere.SkyLight;
import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.geometry.GlossyMask;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.lighting.DeferredLightingSettings;
import com.aryston.helion.render.lighting.LightEnvironment;
import com.aryston.helion.render.voxel.VoxelVolumeLayout;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class RtxIndirectStage implements RenderStage {
    private static final String NAME = "rt_indirect";
    private static final int MATRIX_BYTES = 16 * Float.BYTES;
    private static final int RESOLUTION_DIVISOR = 2;
    private static final long WHOLE_BUFFER = 0L;
    private static final int FRAME_PERIOD = 1 << 16;
    private static final float GI_REACH = 48.0F;
    private static final float SUN_REACH = 96.0F;
    private static final int[] FILTER_STEPS = {1, 2, 4};
    private static final float NIGHT_SKY_SHARE = 0.5F;
    private static final float NIGHT_SKY_FROM = 0.3F;
    private static final float NIGHT_SKY_TO = 0.9F;

    private final RayTracingResources resources;
    private final TerrainSource terrain;
    private final GlossyMask glossy;
    private int frameIndex;
    private @Nullable Matrix4f previousViewProjection;
    private @Nullable Vector3d previousPosition;

    public RtxIndirectStage(RayTracingResources resources, TerrainSource terrain, GlossyMask glossy) {
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
        boolean active = frame.settings().rayTracing().tracesShadows()
            && frame.atmosphere().skyLight().isPresent()
            && frame.geometry().targets().isPresent()
            && resources.available()
            && resources.indirectAvailable()
            && resources.scene().anchor() != null;
        if (!active) {
            previousViewProjection = null;
        }
        return active;
    }

    @Override
    public void addTo(FrameContext frame) {
        RtxSectionPos center = Objects.requireNonNull(resources.scene().anchor());
        SkyLight sky = frame.atmosphere().skyLight().orElseThrow();
        RtxIndirectImages images = resources.indirectImages(
            Math.ceilDiv(frame.scene().width(), RESOLUTION_DIVISOR), Math.ceilDiv(frame.scene().height(), RESOLUTION_DIVISOR)
        );
        images.swap();
        frameIndex = (frameIndex + 1) % FRAME_PERIOD;
        HelionCamera camera = frame.camera();
        ByteBuffer traceConstants = resources.indirectConstants();
        writeTrace(traceConstants, camera, center, sky, frame.scene().light(), frame.settings().lighting(), frameIndex);
        ByteBuffer accumulateConstants = resources.accumulateConstants();
        writeAccumulate(accumulateConstants, camera, images.warm());
        previousViewProjection = new Matrix4f(camera.levelViewProjection());
        previousPosition = new Vector3d(camera.position());
        images.markWarm();
        ByteBuffer filterConstants = resources.filterConstants();
        GeometryBuffer.Targets geometry = frame.geometry().targets().orElseThrow();
        ResourceHandle<RenderTarget> sceneTarget = frame.targets().scene();
        GpuTextureView atlas = terrain.textures().atlas();
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(geometry.normal());
        pass.reads(sceneTarget);
        pass.reads(sky.irradiance());
        pass.disableCulling();
        RtxDevice device = resources.device();
        RtxSectionScene scene = resources.scene();
        long topLevel = scene.topLevelHandle();
        RtxBuffer table = scene.surfaceTable();
        pass.executes(frame.graph().timed(NAME, () -> {
            long depth = handle(Objects.requireNonNull(sceneTarget.get().getDepthTextureView()));
            long normal = handle(Objects.requireNonNull(geometry.normal().get().getColorTextureView()));
            device.memoryBarrier();
            resources.indirectPipeline().dispatch(device.commandBuffer(), List.of(
                new RtxBinding.StorageImage(images.raw().vkImageView()),
                new RtxBinding.Scene(topLevel),
                new RtxBinding.Sampled(depth),
                new RtxBinding.Sampled(normal),
                new RtxBinding.Sampled(handle(atlas)),
                new RtxBinding.StorageBuffer(table.buffer(), WHOLE_BUFFER, table.size()),
                new RtxBinding.Sampled(handle(glossy.update(terrain.glossyCells()))),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(sky.irradiance().get().getColorTextureView())))
            ), traceConstants, images.width(), images.height());
            device.memoryBarrier();
            resources.accumulatePipeline().dispatch(device.commandBuffer(), List.of(
                new RtxBinding.StorageImage(images.history().vkImageView()),
                new RtxBinding.StorageImage(images.guide().vkImageView()),
                new RtxBinding.Sampled(images.raw().vkImageView()),
                new RtxBinding.Sampled(images.previousHistory().vkImageView()),
                new RtxBinding.Sampled(images.previousGuide().vkImageView()),
                new RtxBinding.Sampled(depth),
                new RtxBinding.Sampled(normal)
            ), accumulateConstants, images.width(), images.height());
            RtxImage input = images.history();
            for (int step = 0; step < FILTER_STEPS.length; step++) {
                RtxImage output = images.filter(step % 2);
                filterConstants.clear();
                filterConstants.putInt(FILTER_STEPS[step]).putInt(0).putInt(0).putInt(0).flip();
                device.memoryBarrier();
                resources.filterPipeline().dispatch(device.commandBuffer(), List.of(
                    new RtxBinding.StorageImage(output.vkImageView()),
                    new RtxBinding.Sampled(input.vkImageView()),
                    new RtxBinding.Sampled(images.guide().vkImageView())
                ), filterConstants, images.width(), images.height());
                input = output;
            }
            device.memoryBarrier();
        }));
        frame.voxels().publishPathTraced(images.filter((FILTER_STEPS.length - 1) % 2).view());
    }

    private static long handle(GpuTextureView view) {
        return ((VulkanGpuTextureView) view).vkImageView();
    }

    private static void writeTrace(
        ByteBuffer constants, HelionCamera camera, RtxSectionPos center, SkyLight sky, LightEnvironment light, DeferredLightingSettings settings, int frameIndex
    ) {
        Vector3dc position = camera.position();
        constants.clear();
        camera.levelViewProjection().invert().get(0, constants);
        constants.position(MATRIX_BYTES);
        constants.putFloat((float) (position.x() - center.originX()))
            .putFloat((float) (position.y() - center.originY()))
            .putFloat((float) (position.z() - center.originZ()))
            .putFloat(Math.min(GI_REACH, RtxSectionInbox.RADIUS * VoxelVolumeLayout.SECTION_SIZE));
        Vector3fc sun = sky.sunDirection();
        Vector3fc moon = sky.moonDirection();
        constants.putFloat(sun.x()).putFloat(sun.y()).putFloat(sun.z()).putFloat(frameIndex);
        constants.putFloat(moon.x()).putFloat(moon.y()).putFloat(moon.z()).putFloat(SUN_REACH);
        float skyScale = light.skyFactor() * nightShare(light.skyFactor()) * settings.skyLightIntensity();
        Vector3fc skyColor = light.skyLightColor();
        constants.putFloat(skyColor.x() * skyScale).putFloat(skyColor.y() * skyScale).putFloat(skyColor.z() * skyScale)
            .putFloat(light.blockFactor() * settings.blockLightIntensity());
        constants.flip();
    }

    private void writeAccumulate(ByteBuffer constants, HelionCamera camera, boolean warm) {
        constants.clear();
        camera.levelViewProjection().invert().get(0, constants);
        Matrix4f previous = new Matrix4f().zero();
        Matrix4f last = previousViewProjection;
        Vector3d lastPosition = previousPosition;
        if (warm && last != null && lastPosition != null) {
            Vector3dc position = camera.position();
            previous = new Matrix4f(last).translate(
                (float) (position.x() - lastPosition.x()), (float) (position.y() - lastPosition.y()), (float) (position.z() - lastPosition.z())
            );
        }
        previous.get(MATRIX_BYTES, constants);
        constants.position(2 * MATRIX_BYTES);
        constants.flip();
    }

    private static float nightShare(float skyFactor) {
        float edge = Math.clamp((skyFactor - NIGHT_SKY_FROM) / (NIGHT_SKY_TO - NIGHT_SKY_FROM), 0.0F, 1.0F);
        float smooth = edge * edge * (3.0F - 2.0F * edge);
        return NIGHT_SKY_SHARE + (1.0F - NIGHT_SKY_SHARE) * smooth;
    }
}
