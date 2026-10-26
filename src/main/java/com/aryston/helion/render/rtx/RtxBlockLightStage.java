package com.aryston.helion.render.rtx;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.geometry.GlossyMask;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.voxel.VoxelDynamicLight;
import com.aryston.helion.render.voxel.VoxelDynamicScene;
import com.aryston.helion.render.voxel.VoxelLightingResources;
import com.aryston.helion.render.voxel.VoxelVolumeLayout;
import com.aryston.helion.render.voxel.VoxelVolumeView;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.joml.Vector3dc;
import org.lwjgl.system.MemoryUtil;

public final class RtxBlockLightStage implements RenderStage {
    private static final String NAME = "rt_block_light";
    private static final int MATRIX_BYTES = 16 * Float.BYTES;
    private static final int VECTOR_BYTES = 4 * Float.BYTES;
    private static final int EXTRA_TRACED_LIGHTS = 1;
    private static final long WHOLE_BUFFER = 0L;
    private static final double NANOS_PER_SECOND = 1.0e9;
    private static final double FLICKER_PERIOD_SECONDS = 3600.0;
    private static final int FRAME_PERIOD = 1 << 16;
    private static final float UNUSED = 0.0F;
    private static final float STEADY = 0.0F;
    private static final float FLICKERING = 1.0F;

    private final RayTracingResources resources;
    private final VoxelLightingResources voxels;
    private final TerrainSource terrain;
    private final GlossyMask glossy;
    private int frameIndex;

    public RtxBlockLightStage(RayTracingResources resources, VoxelLightingResources voxels, TerrainSource terrain, GlossyMask glossy) {
        this.resources = resources;
        this.voxels = voxels;
        this.terrain = terrain;
        this.glossy = glossy;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return frame.settings().rayTracing().tracesShadows()
            && frame.voxels().targets().isPresent()
            && frame.geometry().targets().isPresent()
            && voxels.volume().isPresent()
            && resources.available()
            && resources.blockLightAvailable()
            && resources.scene().anchor() != null;
    }

    @Override
    public void addTo(FrameContext frame) {
        RtxSectionPos center = Objects.requireNonNull(resources.scene().anchor());
        VoxelVolumeView view = voxels.volume().orElseThrow();
        int divisor = frame.settings().voxels().quality().resolutionDivisor();
        RtxImage image = resources.blockLight(Math.ceilDiv(frame.scene().width(), divisor), Math.ceilDiv(frame.scene().height(), divisor));
        frameIndex = (frameIndex + 1) % FRAME_PERIOD;
        int budget = frame.settings().voxels().quality().tracedLights() + EXTRA_TRACED_LIGHTS;
        ByteBuffer constants = resources.blockLightConstants();
        writeConstants(constants, frame.camera(), center, view, frameIndex, budget);
        RtxBuffer dynamic = resources.nextBlockLightDynamic();
        writeDynamic(dynamic, voxels.dynamicScene(), view);
        GeometryBuffer.Targets geometry = frame.geometry().targets().orElseThrow();
        ResourceHandle<RenderTarget> sceneTarget = frame.targets().scene();
        GpuTextureView atlas = terrain.textures().atlas();
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(geometry.normal());
        pass.reads(sceneTarget);
        pass.disableCulling();
        RtxDevice device = resources.device();
        RtxSectionScene scene = resources.scene();
        long topLevel = scene.topLevelHandle();
        RtxBuffer table = scene.surfaceTable();
        pass.executes(frame.graph().timed(NAME, () -> {
            device.memoryBarrier();
            GpuTextureView materials = glossy.update(terrain.glossyCells());
            List<RtxBinding> bindings = List.of(
                new RtxBinding.StorageImage(image.vkImageView()),
                new RtxBinding.Scene(topLevel),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(sceneTarget.get().getDepthTextureView()))),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(geometry.normal().get().getColorTextureView()))),
                new RtxBinding.Sampled(handle(atlas)),
                new RtxBinding.StorageBuffer(table.buffer(), WHOLE_BUFFER, table.size()),
                new RtxBinding.Sampled(handle(materials)),
                new RtxBinding.Sampled(handle(view.lights())),
                new RtxBinding.Sampled(handle(view.grid())),
                new RtxBinding.StorageBuffer(dynamic.buffer(), WHOLE_BUFFER, dynamic.size()),
                new RtxBinding.Sampled(handle(view.texture())),
                new RtxBinding.Sampled(handle(view.coarse())),
                new RtxBinding.Sampled(handle(view.colors()))
            );
            resources.blockLightPipeline().dispatch(device.commandBuffer(), bindings, constants, image.width(), image.height());
            device.memoryBarrier();
        }));
        frame.voxels().publishTraced(image.view());
    }

    private static long handle(GpuTextureView view) {
        return ((VulkanGpuTextureView) view).vkImageView();
    }

    private static void writeConstants(ByteBuffer constants, HelionCamera camera, RtxSectionPos center, VoxelVolumeView view, int frameIndex, int budget) {
        Vector3dc position = camera.position();
        constants.clear();
        camera.levelViewProjection().invert().get(0, constants);
        constants.position(MATRIX_BYTES);
        constants.putFloat((float) (position.x() - center.originX()))
            .putFloat((float) (position.y() - center.originY()))
            .putFloat((float) (position.z() - center.originZ()))
            .putFloat(RtxSectionInbox.RADIUS * VoxelVolumeLayout.SECTION_SIZE);
        constants.putFloat((float) (position.x() - view.minBlockX()))
            .putFloat((float) (position.y() - view.minBlockY()))
            .putFloat((float) (position.z() - view.minBlockZ()))
            .putFloat(UNUSED);
        constants.putInt(view.minBlockX()).putInt(view.minBlockY()).putInt(view.minBlockZ()).putInt(view.lightCount());
        constants.putFloat(frameIndex)
            .putFloat((float) (System.nanoTime() / NANOS_PER_SECOND % FLICKER_PERIOD_SECONDS))
            .putFloat(budget)
            .putFloat(UNUSED);
        constants.flip();
    }

    private static void writeDynamic(RtxBuffer buffer, VoxelDynamicScene scene, VoxelVolumeView view) {
        long base = buffer.mapped();
        int count = Math.min(scene.lights().size(), VoxelDynamicScene.MAX_LIGHTS);
        MemoryUtil.memPutInt(base, count);
        long offset = base + VECTOR_BYTES;
        for (int index = 0; index < count; index++) {
            VoxelDynamicLight light = scene.lights().get(index);
            putVector(offset, (float) (light.x() - view.minBlockX()), (float) (light.y() - view.minBlockY()), (float) (light.z() - view.minBlockZ()), light.level());
            putVector(offset + VECTOR_BYTES, light.red(), light.green(), light.blue(), light.flickers() ? FLICKERING : STEADY);
            offset += 2L * VECTOR_BYTES;
        }
    }

    private static void putVector(long address, float x, float y, float z, float w) {
        MemoryUtil.memPutFloat(address, x);
        MemoryUtil.memPutFloat(address + Float.BYTES, y);
        MemoryUtil.memPutFloat(address + 2L * Float.BYTES, z);
        MemoryUtil.memPutFloat(address + 3L * Float.BYTES, w);
    }
}
