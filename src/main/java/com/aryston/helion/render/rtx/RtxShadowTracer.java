package com.aryston.helion.render.rtx;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.shadow.ShadowLight;
import com.aryston.helion.render.shadow.TracedShadows;
import com.aryston.helion.render.voxel.VoxelVolumeLayout;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

public final class RtxShadowTracer implements TracedShadows {
    static final String NAME = "rt_sun_shadow";

    private static final int RESOLUTION_DIVISOR = 2;
    private static final int MATRIX_BYTES = 16 * Float.BYTES;
    private static final float MAX_DISTANCE = 128.0F;
    private static final float SOFTNESS = 0.012F;
    private static final float UNUSED = 0.0F;
    private static final long WHOLE_BUFFER = 0L;

    private final RayTracingResources resources;
    private final TerrainSource terrain;

    public RtxShadowTracer(RayTracingResources resources, TerrainSource terrain) {
        this.resources = resources;
        this.terrain = terrain;
    }

    @Override
    public Optional<GpuTextureView> addTo(FrameContext frame, ShadowLight light) {
        boolean wanted = frame.settings().rayTracing().tracesShadows() || (RayTracing.VIEW && frame.settings().rayTracing().active());
        if (!wanted || frame.geometry().targets().isEmpty() || !resources.available() || resources.scene().anchor() == null) {
            return Optional.empty();
        }
        RtxSectionPos center = Objects.requireNonNull(resources.scene().anchor());
        int width = Math.ceilDiv(frame.scene().width(), RESOLUTION_DIVISOR);
        int height = Math.ceilDiv(frame.scene().height(), RESOLUTION_DIVISOR);
        RtxImage mask = resources.mask(width, height);
        ByteBuffer constants = resources.constants();
        writeConstants(constants, frame.camera(), center, light.direction());
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
            List<RtxBinding> bindings = List.of(
                new RtxBinding.StorageImage(mask.vkImageView()),
                new RtxBinding.Scene(topLevel),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(sceneTarget.get().getDepthTextureView()))),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(geometry.normal().get().getColorTextureView()))),
                new RtxBinding.Sampled(handle(atlas)),
                new RtxBinding.StorageBuffer(table.buffer(), WHOLE_BUFFER, table.size())
            );
            resources.sunShadow().dispatch(device.commandBuffer(), bindings, constants, mask.width(), mask.height());
            device.memoryBarrier();
        }));
        return frame.settings().rayTracing().tracesShadows() ? Optional.of(mask.view()) : Optional.empty();
    }

    private static long handle(GpuTextureView view) {
        return ((VulkanGpuTextureView) view).vkImageView();
    }

    private static void writeConstants(ByteBuffer constants, HelionCamera camera, RtxSectionPos center, Vector3fc toLight) {
        Vector3dc position = camera.position();
        constants.clear();
        camera.levelViewProjection().invert().get(0, constants);
        constants.position(MATRIX_BYTES);
        constants.putFloat((float) (position.x() - center.originX()))
            .putFloat((float) (position.y() - center.originY()))
            .putFloat((float) (position.z() - center.originZ()))
            .putFloat(RtxSectionInbox.RADIUS * VoxelVolumeLayout.SECTION_SIZE);
        constants.putFloat(toLight.x()).putFloat(toLight.y()).putFloat(toLight.z()).putFloat(MAX_DISTANCE);
        constants.putFloat(SOFTNESS).putFloat(UNUSED).putFloat(UNUSED).putFloat(UNUSED);
        constants.flip();
    }
}
