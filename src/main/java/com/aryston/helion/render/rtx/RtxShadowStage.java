package com.aryston.helion.render.rtx;

import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.voxel.VoxelLightingResources;
import com.aryston.helion.render.voxel.VoxelVolumeLayout;
import com.aryston.helion.render.voxel.VoxelVolumeView;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;

public final class RtxShadowStage implements RenderStage {
    static final String BOTTOM_STAGE = "rtx_blas";
    static final String TOP_STAGE = "rtx_tlas";
    static final String VOXEL_LIGHT_STAGE = "voxel_light";

    private static final String NAME = "rtx_shadow";
    private static final int RESOLUTION_DIVISOR = 2;
    private static final int MATRIX_BYTES = 16 * Float.BYTES;
    private static final int UNUSED = 0;
    private static final long NO_OFFSET = 0L;

    private final RtxSpikeResources resources;
    private final VoxelLightingResources voxels;
    private @Nullable VoxelVolumeView volume;

    public RtxShadowStage(RtxSpikeResources resources, VoxelLightingResources voxels) {
        this.resources = resources;
        this.voxels = voxels;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!RtxSpike.ENABLED || !frame.settings().voxels().enabled() || frame.geometry().targets().isEmpty()) {
            return false;
        }
        Optional<VoxelVolumeView> view = voxels.volume();
        volume = view.orElse(null);
        return view.isPresent() && resources.available();
    }

    @Override
    public void addTo(FrameContext frame) {
        VoxelVolumeView view = Objects.requireNonNull(volume);
        HelionCamera camera = frame.camera();
        Vector3dc position = camera.position();
        RtxArea area = RtxBlocks.area((int) Math.floor(position.x()), view.minBlockY(), (int) Math.floor(position.z()));
        RtxScene scene = resources.scene(area);
        boolean build = !scene.built();
        RtxTraceMode mode = resources.nextMode();
        int width = Math.ceilDiv(frame.scene().width(), RESOLUTION_DIVISOR);
        int height = Math.ceilDiv(frame.scene().height(), RESOLUTION_DIVISOR);
        RtxShadowMask mask = resources.mask(width, height);
        long agreementOffset = resources.agreement().beginSlotFor(mode);
        writeConstants(resources.constants(), camera, view, area, mode);
        GeometryBuffer.Targets geometry = frame.geometry().targets().orElseThrow();
        ResourceHandle<RenderTarget> sceneTarget = frame.targets().scene();
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(geometry.normal());
        pass.reads(sceneTarget);
        pass.disableCulling();
        RtxDevice device = resources.device();
        Runnable bottom = frame.graph().timed(BOTTOM_STAGE, () -> {
            device.memoryBarrier();
            scene.recordBottom(device.commandBuffer());
            device.memoryBarrier();
        });
        Runnable top = frame.graph().timed(TOP_STAGE, () -> {
            scene.recordTop(device.commandBuffer());
            device.memoryBarrier();
        });
        Runnable trace = frame.graph().timed(mode.stage(), () -> {
            device.memoryBarrier();
            List<RtxBinding> bindings = new ArrayList<>(List.of(
                new RtxBinding.StorageImage(mask.vkImageView()),
                new RtxBinding.Scene(scene.topLevel()),
                new RtxBinding.StorageBuffer(resources.agreement().buffer(), agreementOffset, resources.agreement().range()),
                new RtxBinding.StorageBuffer(scene.boxBuffer(), NO_OFFSET, scene.boxBytes())
            ));
            for (long sampled : sampledViews(sceneTarget, geometry.normal(), view)) {
                bindings.add(new RtxBinding.Sampled(sampled));
            }
            resources.pipeline().dispatch(device.commandBuffer(), bindings, resources.constants(), mask.width(), mask.height());
            device.memoryBarrier();
        });
        pass.executes(() -> {
            if (build) {
                bottom.run();
                top.run();
            }
            trace.run();
        });
        resources.logIfDue(HelionRenderCore.get().timings().averageMillis());
    }

    private static long[] sampledViews(ResourceHandle<RenderTarget> sceneTarget, ResourceHandle<RenderTarget> normal, VoxelVolumeView view) {
        return new long[] {
            handle(Objects.requireNonNull(sceneTarget.get().getDepthTextureView())),
            handle(Objects.requireNonNull(normal.get().getColorTextureView())),
            handle(view.texture()),
            handle(view.coarse()),
            handle(view.ready()),
            handle(view.lights()),
            handle(view.grid())
        };
    }

    private static long handle(GpuTextureView view) {
        return ((VulkanGpuTextureView) view).vkImageView();
    }

    private static void writeConstants(ByteBuffer constants, HelionCamera camera, VoxelVolumeView view, RtxArea area, RtxTraceMode mode) {
        Matrix4f inverse = camera.levelViewProjection().invert();
        Vector3dc position = camera.position();
        constants.clear();
        inverse.get(0, constants);
        constants.position(MATRIX_BYTES);
        constants.putFloat((float) (position.x() - view.minBlockX()))
            .putFloat((float) (position.y() - view.minBlockY()))
            .putFloat((float) (position.z() - view.minBlockZ()))
            .putFloat(VoxelVolumeLayout.VOLUME_SIZE);
        constants.putInt(view.minBlockX()).putInt(view.minBlockY()).putInt(view.minBlockZ()).putInt(mode.ordinal());
        constants.putInt(area.minX() - view.minBlockX()).putInt(area.minY() - view.minBlockY()).putInt(area.minZ() - view.minBlockZ()).putInt(UNUSED);
        constants.putInt(RtxBlocks.AREA_WIDTH).putInt(RtxBlocks.AREA_HEIGHT).putInt(RtxBlocks.AREA_WIDTH).putInt(UNUSED);
        constants.flip();
    }
}
