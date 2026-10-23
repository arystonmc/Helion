package com.aryston.helion.render.rtx;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.geometry.TerrainTextures;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.voxel.VoxelVolumeLayout;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;

public final class RtxReflectionStage implements RenderStage {
    static final String NAME = "rt_reflection";

    private static final int MATRIX_BYTES = 16 * Float.BYTES;
    private static final float MAX_DISTANCE = 96.0F;
    private static final long WHOLE_BUFFER = 0L;
    private static final float UNUSED = 0.0F;

    private final RayTracingResources resources;
    private final TerrainSource terrain;
    private @Nullable RtxSectionPos anchor;

    public RtxReflectionStage(RayTracingResources resources, TerrainSource terrain) {
        this.resources = resources;
        this.terrain = terrain;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!RayTracing.ENABLED || frame.water().surface().isEmpty() || !resources.available() || !resources.reflectionAvailable()) {
            return false;
        }
        anchor = resources.scene().anchor();
        return anchor != null;
    }

    @Override
    public void addTo(FrameContext frame) {
        RtxSectionPos center = Objects.requireNonNull(anchor);
        RtxImage image = resources.reflection(frame.scene().width(), frame.scene().height());
        ByteBuffer constants = resources.reflectionConstants();
        writeConstants(constants, frame.camera(), center);
        ResourceHandle<RenderTarget> surface = frame.water().surface().orElseThrow();
        TerrainTextures textures = terrain.textures();
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(surface);
        pass.disableCulling();
        RtxDevice device = resources.device();
        RtxSectionScene scene = resources.scene();
        long topLevel = scene.topLevelHandle();
        RtxBuffer table = scene.surfaceTable();
        pass.executes(frame.graph().timed(NAME, () -> {
            device.memoryBarrier();
            RenderTarget water = surface.get();
            List<RtxBinding> bindings = List.of(
                new RtxBinding.StorageImage(image.vkImageView()),
                new RtxBinding.Scene(topLevel),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(water.getDepthTextureView()))),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(water.getColorTextureView()))),
                new RtxBinding.Sampled(handle(textures.atlas())),
                new RtxBinding.Sampled(handle(textures.lightmap())),
                new RtxBinding.StorageBuffer(table.buffer(), WHOLE_BUFFER, table.size())
            );
            resources.reflectionPipeline().dispatch(device.commandBuffer(), bindings, constants, image.width(), image.height());
            device.memoryBarrier();
        }));
        frame.water().publishReflections(image.view());
    }

    private static long handle(GpuTextureView view) {
        return ((VulkanGpuTextureView) view).vkImageView();
    }

    private static void writeConstants(ByteBuffer constants, HelionCamera camera, RtxSectionPos center) {
        Vector3dc position = camera.position();
        constants.clear();
        camera.levelViewProjection().invert().get(0, constants);
        constants.position(MATRIX_BYTES);
        constants.putFloat((float) (position.x() - center.originX()))
            .putFloat((float) (position.y() - center.originY()))
            .putFloat((float) (position.z() - center.originZ()))
            .putFloat(RtxSectionInbox.RADIUS * VoxelVolumeLayout.SECTION_SIZE);
        constants.putFloat(MAX_DISTANCE).putFloat(UNUSED).putFloat(UNUSED).putFloat(UNUSED);
        constants.flip();
    }
}
