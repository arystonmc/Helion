package com.aryston.helion.render.rtx;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shadow.ShadowLight;
import com.aryston.helion.render.shadow.ShadowResults;
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
import org.jspecify.annotations.Nullable;

public final class RtxSunShadowStage implements RenderStage {
    static final String NAME = "rt_sun_shadow";

    private static final int RESOLUTION_DIVISOR = 2;
    private static final int MATRIX_BYTES = 16 * Float.BYTES;
    private static final float MAX_DISTANCE = 128.0F;

    private final RayTracingResources resources;
    private @Nullable ShadowLight light;
    private @Nullable RtxSectionPos anchor;

    public RtxSunShadowStage(RayTracingResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!RayTracing.ENABLED || frame.geometry().targets().isEmpty() || !resources.available()) {
            return false;
        }
        Optional<ShadowResults.Shadows> shadows = frame.shadows().shadows();
        light = shadows.map(ShadowResults.Shadows::light).orElse(null);
        anchor = resources.scene().anchor();
        return light != null && anchor != null;
    }

    @Override
    public void addTo(FrameContext frame) {
        ShadowLight shadowLight = Objects.requireNonNull(light);
        RtxSectionPos center = Objects.requireNonNull(anchor);
        int width = Math.ceilDiv(frame.scene().width(), RESOLUTION_DIVISOR);
        int height = Math.ceilDiv(frame.scene().height(), RESOLUTION_DIVISOR);
        RtxImage mask = resources.mask(width, height);
        ByteBuffer constants = resources.constants();
        writeConstants(constants, frame.camera(), center, shadowLight.direction());
        GeometryBuffer.Targets geometry = frame.geometry().targets().orElseThrow();
        ResourceHandle<RenderTarget> sceneTarget = frame.targets().scene();
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(geometry.normal());
        pass.reads(sceneTarget);
        pass.disableCulling();
        RtxDevice device = resources.device();
        long topLevel = resources.scene().topLevelHandle();
        pass.executes(frame.graph().timed(NAME, () -> {
            device.memoryBarrier();
            List<RtxBinding> bindings = List.of(
                new RtxBinding.StorageImage(mask.vkImageView()),
                new RtxBinding.Scene(topLevel),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(sceneTarget.get().getDepthTextureView()))),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(geometry.normal().get().getColorTextureView())))
            );
            resources.sunShadow().dispatch(device.commandBuffer(), bindings, constants, mask.width(), mask.height());
            device.memoryBarrier();
        }));
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
        constants.flip();
    }
}
