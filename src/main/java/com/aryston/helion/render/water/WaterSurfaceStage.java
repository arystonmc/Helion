package com.aryston.helion.render.water;

import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.rtx.RayTracing;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.Objects;
import org.joml.Vector4f;

public final class WaterSurfaceStage implements RenderStage {
    private static final String NAME = "water_surface";
    private static final String PASS_LABEL = "Helion Water Surface";
    private static final String TARGET = "helion:water_surface";

    private final TerrainSource terrain;
    private final WaterResources resources;

    public WaterSurfaceStage(TerrainSource terrain, WaterResources resources) {
        this.terrain = terrain;
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return RayTracing.ENABLED
            && frame.scene().colorFormat() == WaterPipelines.SCENE_COLOR_FORMAT
            && terrain.supportsWaterSurface();
    }

    @Override
    public void addTo(FrameContext frame) {
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        pass.reads(scene);
        ResourceHandle<RenderTarget> surface = pass.createsInternal(TARGET, new RenderTargetDescriptor(
            frame.scene().width(),
            frame.scene().height(),
            new RenderTargetDescriptor.TextureProperties(new Vector4f(), WaterPipelines.SURFACE_FORMAT),
            RenderTargetDescriptor.TextureProperties.DEFAULT_DEPTH
        ));
        frame.water().publishSurface(surface);
        pass.executes(frame.graph().timed(NAME, () -> render(scene.get(), surface.get())));
    }

    private void render(RenderTarget scene, RenderTarget surface) {
        RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> PASS_LABEL)
            .withColorAttachment(Objects.requireNonNull(surface.getColorTextureView()))
            .withDepthAttachment(Objects.requireNonNull(surface.getDepthTextureView()))
            .build();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(WaterPipelines.SURFACE_UNIFORM, resources.surfaceUniforms(terrain.textures()));
            pass.setUniform(
                WaterPipelines.SCENE_DEPTH_SAMPLER,
                Objects.requireNonNull(scene.getDepthTextureView()),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            );
            terrain.renderWaterSurface(pass);
        }
        resources.finishSurface();
    }
}
