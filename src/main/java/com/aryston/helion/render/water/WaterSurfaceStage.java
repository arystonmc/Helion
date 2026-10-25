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
    private static final String GLASS_TARGET = "helion:glass_surface";
    private static final String GLASS_TINT_TARGET = "helion:glass_tint";
    private static final String GLASS_PASS_LABEL = "Helion Glass Surface";
    private static final boolean WATER_PASS = false;
    private static final boolean GLASS_PASS = true;
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
        ResourceHandle<RenderTarget> surface = pass.createsInternal(TARGET, surfaceDescriptor(frame));
        ResourceHandle<RenderTarget> tint = pass.createsInternal(TINT_TARGET, colorDescriptor(frame, WaterPipelines.TINT_FORMAT));
        ResourceHandle<RenderTarget> glassSurface = pass.createsInternal(GLASS_TARGET, surfaceDescriptor(frame));
        ResourceHandle<RenderTarget> glassTint = pass.createsInternal(GLASS_TINT_TARGET, colorDescriptor(frame, WaterPipelines.TINT_FORMAT));
        Optional<ResourceHandle<RenderTarget>> refraction = frame.settings().reflections().realisticWater()
            ? Optional.of(pass.createsInternal(REFRACTION_TARGET, colorDescriptor(frame, WaterPipelines.SCENE_COLOR_FORMAT)))
            : Optional.empty();
        frame.water().publishSurface(surface);
        frame.water().publishTint(tint);
        frame.water().publishGlass(glassSurface, glassTint);
        refraction.ifPresent(frame.water()::publishRefraction);
        Wetness wetness = frame.water().wetness().orElse(Wetness.DRY);
        Optional<ResourceHandle<RenderTarget>> waves = frame.water().waves();
        waves.ifPresent(pass::reads);
        pass.executes(frame.graph().timed(NAME, () -> {
            refraction.ifPresent(copy -> copyScene(scene.get(), copy.get()));
            GpuTextureView mask = glossy.update(terrain.glossyCells());
            render(scene.get(), surface.get(), tint.get(), mask, wetness, waves.map(ResourceHandle::get));
            renderGlass(scene.get(), glassSurface.get(), glassTint.get(), mask, wetness, waves.map(ResourceHandle::get));
            resources.finishSurface();
        }));
    }

    private static RenderTargetDescriptor surfaceDescriptor(FrameContext frame) {
        return new RenderTargetDescriptor(
            frame.scene().width(),
            frame.scene().height(),
            new RenderTargetDescriptor.TextureProperties(new Vector4f(), WaterPipelines.SURFACE_FORMAT),
            RenderTargetDescriptor.TextureProperties.DEFAULT_DEPTH
        );
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

    private void render(RenderTarget scene, RenderTarget surface, RenderTarget tint, GpuTextureView mask, Wetness wetness, Optional<RenderTarget> waves) {
        try (RenderPass pass = openPass(PASS_LABEL, scene, surface, tint, mask, wetness, waves, WATER_PASS)) {
            terrain.renderWaterSurface(pass);
        }
    }

    private void renderGlass(RenderTarget scene, RenderTarget surface, RenderTarget tint, GpuTextureView mask, Wetness wetness, Optional<RenderTarget> waves) {
        try (RenderPass pass = openPass(GLASS_PASS_LABEL, scene, surface, tint, mask, wetness, waves, GLASS_PASS)) {
            terrain.renderWaterSurface(pass);
        }
    }

    private RenderPass openPass(String label, RenderTarget scene, RenderTarget surface, RenderTarget tint, GpuTextureView mask, Wetness wetness, Optional<RenderTarget> waves, boolean glassPass) {
        RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> label)
            .withColorAttachment(Objects.requireNonNull(surface.getColorTextureView()))
            .withColorAttachment(Objects.requireNonNull(tint.getColorTextureView()))
            .withDepthAttachment(Objects.requireNonNull(surface.getDepthTextureView()))
            .build();
        RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform(WaterPipelines.SURFACE_UNIFORM, resources.surfaceUniforms(terrain.textures(), wetness, waves.isPresent(), glassPass));
        GpuTextureView waveView = waves.map(RenderTarget::getColorTextureView).orElse(scene.getColorTextureView());
        pass.setUniform(WaterPipelines.WAVE_SAMPLER, Objects.requireNonNull(waveView), RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
        pass.setUniform(
            WaterPipelines.SCENE_DEPTH_SAMPLER,
            Objects.requireNonNull(scene.getDepthTextureView()),
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
        );
        pass.setUniform(GeometryBufferPipelines.GLOSSY_SAMPLER, mask, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        GpuTextureView shoreView = waves.flatMap(ignored -> resources.waves().shore().view()).orElse(scene.getColorTextureView());
        pass.setUniform(WaterPipelines.SHORE_SAMPLER, Objects.requireNonNull(shoreView), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        GpuTextureView distanceView = waves.flatMap(ignored -> resources.waves().shore().distanceView()).orElse(scene.getColorTextureView());
        pass.setUniform(WaterPipelines.SHORE_DISTANCE_SAMPLER, Objects.requireNonNull(distanceView), RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
        return pass;
    }
}
