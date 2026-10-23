package com.aryston.helion.render.water;

import com.aryston.helion.render.geometry.GeometryBufferPipelines;
import com.aryston.helion.render.geometry.GlossyMask;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import org.joml.Vector4f;

public final class WaterSurfaceStage implements RenderStage {
    private static final String NAME = "water_surface";
    private static final String PASS_LABEL = "Helion Water Surface";
    private static final String TARGET = "helion:water_surface";
    private static final String TINT_TARGET = "helion:water_tint";
    private static final String REFRACTION_TARGET = "helion:water_refraction";
    private static final int BASE_LEVEL = 0;
    private static final int ORIGIN = 0;

    private final TerrainSource terrain;
    private final WaterResources resources;
    private final GlossyMask glossy;

    public WaterSurfaceStage(TerrainSource terrain, WaterResources resources, GlossyMask glossy) {
        this.terrain = terrain;
        this.resources = resources;
        this.glossy = glossy;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return (frame.settings().rayTracing().reflectsWater() || frame.settings().reflections().screenSpace() || frame.settings().reflections().realisticWater())
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
        ResourceHandle<RenderTarget> tint = pass.createsInternal(TINT_TARGET, colorDescriptor(frame, WaterPipelines.TINT_FORMAT));
        Optional<ResourceHandle<RenderTarget>> refraction = frame.settings().reflections().realisticWater()
            ? Optional.of(pass.createsInternal(REFRACTION_TARGET, colorDescriptor(frame, WaterPipelines.SCENE_COLOR_FORMAT)))
            : Optional.empty();
        frame.water().publishSurface(surface);
        frame.water().publishTint(tint);
        refraction.ifPresent(frame.water()::publishRefraction);
        Wetness wetness = frame.water().wetness().orElse(Wetness.DRY);
        Optional<ResourceHandle<RenderTarget>> waves = frame.water().waves();
        waves.ifPresent(pass::reads);
        pass.executes(frame.graph().timed(NAME, () -> {
            refraction.ifPresent(copy -> copyScene(scene.get(), copy.get()));
            render(scene.get(), surface.get(), tint.get(), wetness, waves.map(ResourceHandle::get));
        }));
    }

    private static RenderTargetDescriptor colorDescriptor(FrameContext frame, GpuFormat format) {
        return new RenderTargetDescriptor(frame.scene().width(), frame.scene().height(), new RenderTargetDescriptor.TextureProperties(new Vector4f(), format), null);
    }

    private static void copyScene(RenderTarget scene, RenderTarget copy) {
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            Objects.requireNonNull(scene.getColorTexture()), Objects.requireNonNull(copy.getColorTexture()), BASE_LEVEL, ORIGIN, ORIGIN, ORIGIN, ORIGIN,
            scene.width, scene.height
        );
    }

    private void render(RenderTarget scene, RenderTarget surface, RenderTarget tint, Wetness wetness, Optional<RenderTarget> waves) {
        GpuTextureView mask = glossy.update(terrain.glossyCells());
        RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> PASS_LABEL)
            .withColorAttachment(Objects.requireNonNull(surface.getColorTextureView()))
            .withColorAttachment(Objects.requireNonNull(tint.getColorTextureView()))
            .withDepthAttachment(Objects.requireNonNull(surface.getDepthTextureView()))
            .build();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(WaterPipelines.SURFACE_UNIFORM, resources.surfaceUniforms(terrain.textures(), wetness, waves.isPresent()));
            GpuTextureView waveView = waves.map(RenderTarget::getColorTextureView).orElse(scene.getColorTextureView());
            pass.setUniform(WaterPipelines.WAVE_SAMPLER, Objects.requireNonNull(waveView), RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
            pass.setUniform(
                WaterPipelines.SCENE_DEPTH_SAMPLER,
                Objects.requireNonNull(scene.getDepthTextureView()),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            );
            pass.setUniform(GeometryBufferPipelines.GLOSSY_SAMPLER, mask, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            terrain.renderWaterSurface(pass);
        }
        resources.finishSurface();
    }
}
