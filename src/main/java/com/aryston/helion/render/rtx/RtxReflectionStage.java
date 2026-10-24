package com.aryston.helion.render.rtx;

import com.aryston.helion.render.atmosphere.SkyLight;
import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.geometry.TerrainTextures;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.voxel.VoxelVolumeLayout;
import com.aryston.helion.render.water.Wetness;
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
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class RtxReflectionStage implements RenderStage {
    static final String NAME = "rt_reflection";

    private static final int MATRIX_BYTES = 16 * Float.BYTES;
    private static final float MAX_DISTANCE = 96.0F;
    private static final long WHOLE_BUFFER = 0L;
    private static final float UNUSED = 0.0F;
    private static final float TRACED = 1.0F;
    private static final float NO_PUDDLES = 0.0F;
    private static final double NOISE_PERIOD = 1024.0;
    private static final float HORIZON = 0.0F;
    private static final Vector3fc NO_LIGHT = new Vector3f(0.0F, 1.0F, 0.0F);

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
        if (!frame.settings().rayTracing().reflectsWater() || frame.water().surface().isEmpty() || !resources.available() || !resources.reflectionAvailable()) {
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
        Optional<GeometryBuffer.Targets> geometry = frame.geometry().targets();
        float puddles = geometry.isPresent() ? frame.water().wetness().map(Wetness::puddles).orElse(NO_PUDDLES) : NO_PUDDLES;
        writeConstants(constants, frame.camera(), center, frame.atmosphere().skyLight(), puddles);
        ResourceHandle<RenderTarget> surface = frame.water().surface().orElseThrow();
        TerrainTextures textures = terrain.textures();
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(surface);
        ResourceHandle<RenderTarget> sceneTarget = frame.targets().scene();
        pass.reads(sceneTarget);
        geometry.ifPresent(targets -> {
            pass.reads(targets.normal());
            pass.reads(targets.light());
        });
        pass.disableCulling();
        RtxDevice device = resources.device();
        RtxSectionScene scene = resources.scene();
        long topLevel = scene.topLevelHandle();
        RtxBuffer table = scene.surfaceTable();
        pass.executes(frame.graph().timed(NAME, () -> {
            device.memoryBarrier();
            RenderTarget water = surface.get();
            List<RtxBinding> bindings = new ArrayList<>(List.of(
                new RtxBinding.StorageImage(image.vkImageView()),
                new RtxBinding.Scene(topLevel),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(water.getDepthTextureView()))),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(water.getColorTextureView()))),
                new RtxBinding.Sampled(handle(textures.atlas())),
                new RtxBinding.Sampled(handle(textures.lightmap())),
                new RtxBinding.StorageBuffer(table.buffer(), WHOLE_BUFFER, table.size()),
                new RtxBinding.Sampled(handle(Objects.requireNonNull(sceneTarget.get().getDepthTextureView()))),
                new RtxBinding.Sampled(geometry.map(targets -> handle(colorView(targets.normal()))).orElse(handle(Objects.requireNonNull(water.getColorTextureView())))),
                new RtxBinding.Sampled(geometry.map(targets -> handle(colorView(targets.light()))).orElse(handle(Objects.requireNonNull(water.getColorTextureView()))))
            ));
            if (device.entityTextures()) {
                bindings.addAll(entityBindings(scene.entitySurfaces(), handle(textures.atlas())));
            }
            resources.reflectionPipeline().dispatch(device.commandBuffer(), bindings, constants, image.width(), image.height());
            device.memoryBarrier();
        }));
        frame.water().publishReflections(image.view());
    }

    private static List<RtxBinding> entityBindings(RtxEntitySurfaces entities, long fallbackView) {
        RtxBuffer entityTable = entities.table();
        return List.of(
            new RtxBinding.SampledArray(entities.views(fallbackView)),
            new RtxBinding.StorageBuffer(entityTable.buffer(), WHOLE_BUFFER, entityTable.size())
        );
    }

    private static Vector3fc shadowLight(SkyLight sky) {
        return sky.sunDirection().y() > HORIZON ? sky.sunDirection() : sky.moonDirection();
    }

    private static long handle(GpuTextureView view) {
        return ((VulkanGpuTextureView) view).vkImageView();
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }

    private static void writeConstants(ByteBuffer constants, HelionCamera camera, RtxSectionPos center, Optional<SkyLight> sky, float puddles) {
        Vector3dc position = camera.position();
        constants.clear();
        camera.levelViewProjection().invert().get(0, constants);
        constants.position(MATRIX_BYTES);
        constants.putFloat((float) (position.x() - center.originX()))
            .putFloat((float) (position.y() - center.originY()))
            .putFloat((float) (position.z() - center.originZ()))
            .putFloat(RtxSectionInbox.RADIUS * VoxelVolumeLayout.SECTION_SIZE);
        constants.putFloat(MAX_DISTANCE).putFloat((float) (position.x() % NOISE_PERIOD)).putFloat((float) (position.z() % NOISE_PERIOD)).putFloat(puddles);
        Optional<Vector3fc> light = sky.map(RtxReflectionStage::shadowLight).filter(direction -> direction.y() > HORIZON);
        Vector3fc direction = light.orElse(NO_LIGHT);
        constants.putFloat(direction.x()).putFloat(direction.y()).putFloat(direction.z()).putFloat(light.isPresent() ? TRACED : UNUSED);
        constants.flip();
    }
}
