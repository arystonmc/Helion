package com.aryston.helion.render.geometry;

import com.aryston.helion.render.atmosphere.AtmosphereSource;
import com.aryston.helion.render.atmosphere.SceneFogPass;
import com.aryston.helion.render.clouds.SceneCloudPass;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.lightshaft.SceneLightShaftPass;
import com.aryston.helion.render.water.SceneReflectionPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public final class TransparentGeometryStage implements RenderStage {
    private static final String NAME = "transparent_geometry";
    private static final String PASS_LABEL = "Helion Transparent Geometry";

    private final TerrainSource terrain;
    private final EntitySource entities;
    private final AtmosphereSource atmosphere;
    private final TransparencySource transparency;
    private final GlossyMask glossy;

    public TransparentGeometryStage(TerrainSource terrain, EntitySource entities, AtmosphereSource atmosphere, TransparencySource transparency, GlossyMask glossy) {
        this.terrain = terrain;
        this.entities = entities;
        this.atmosphere = atmosphere;
        this.transparency = transparency;
        this.glossy = glossy;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return true;
    }

    @Override
    public void addTo(FrameContext frame) {
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        frame.targets().declareGeometryAttachments(pass);
        AtmospherePasses atmospherePasses = new AtmospherePasses(
            frame.water().reflectionPass(), frame.atmosphere().fog(), frame.atmosphere().clouds(), frame.atmosphere().lightShafts()
        );
        atmospherePasses.declareReads(pass);
        boolean orderIndependent = frame.scene().orderIndependentTransparency();
        boolean featuresDrawn = TranslucentFeatureStage.drawsBeforeWater(frame);
        boolean seeThroughGlass = !orderIndependent && frame.settings().reflections().realisticWater() && terrain.supportsWaterSurface();
        pass.executes(frame.graph().timed(NAME, () -> render(scene.get(), orderIndependent, featuresDrawn, atmospherePasses, seeThroughGlass)));
    }

    private void render(RenderTarget target, boolean orderIndependent, boolean featuresDrawn, AtmospherePasses atmospherePasses, boolean seeThroughGlass) {
        Optional<GpuTextureView> mask = seeThroughGlass ? Optional.of(glossy.update(terrain.glossyCells())) : Optional.empty();
        if (orderIndependent) {
            transparency.renderOrderIndependent();
            atmospherePasses.draw(target);
        } else if (!atmospherePasses.isEmpty()) {
            renderSortedThroughFog(target, featuresDrawn, atmospherePasses, mask);
        } else {
            renderSorted(target, featuresDrawn, mask);
        }
        entities.renderOverlays(target);
    }

    private void renderSorted(RenderTarget target, boolean featuresDrawn, Optional<GpuTextureView> mask) {
        try (RenderPass pass = openPass(target)) {
            renderTranslucents(pass, featuresDrawn, mask);
            renderAtmosphere(pass);
        }
    }

    private void renderSortedThroughFog(RenderTarget target, boolean featuresDrawn, AtmospherePasses atmospherePasses, Optional<GpuTextureView> mask) {
        try (RenderPass pass = openPass(target)) {
            renderTranslucents(pass, featuresDrawn, mask);
        }
        atmospherePasses.draw(target);
        try (RenderPass pass = openPass(target)) {
            renderAtmosphere(pass);
        }
    }

    private void renderTranslucents(RenderPass pass, boolean featuresDrawn, Optional<GpuTextureView> mask) {
        if (!featuresDrawn) {
            entities.renderTranslucent(pass);
        }
        terrain.renderTranslucent(pass, mask);
        entities.renderTranslucentAfterTerrain(pass);
    }

    private void renderAtmosphere(RenderPass pass) {
        atmosphere.renderClouds(pass);
        atmosphere.renderWeather(pass);
        atmosphere.renderWorldBorder(pass);
    }

    private static RenderPass openPass(RenderTarget target) {
        RenderPass pass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                () -> PASS_LABEL,
                Objects.requireNonNull(target.getColorTextureView()),
                Optional.empty(),
                target.getDepthTextureView(),
                OptionalDouble.empty()
            );
        RenderSystem.bindDefaultUniforms(pass);
        return pass;
    }

    private record AtmospherePasses(
        Optional<SceneReflectionPass> reflection,
        Optional<SceneFogPass> fog,
        Optional<SceneCloudPass> clouds,
        Optional<SceneLightShaftPass> lightShafts
    ) {
        boolean isEmpty() {
            return reflection.isEmpty() && fog.isEmpty() && clouds.isEmpty() && lightShafts.isEmpty();
        }

        void declareReads(FramePass pass) {
            reflection.ifPresent(reflectionPass -> reflectionPass.declareReads(pass));
            fog.ifPresent(fogPass -> fogPass.declareReads(pass));
            clouds.ifPresent(cloudPass -> cloudPass.declareReads(pass));
            lightShafts.ifPresent(shaftPass -> shaftPass.declareReads(pass));
        }

        void draw(RenderTarget target) {
            reflection.ifPresent(reflectionPass -> reflectionPass.draw(target));
            fog.ifPresent(fogPass -> fogPass.draw(target));
            clouds.ifPresent(cloudPass -> cloudPass.draw(target));
            lightShafts.ifPresent(shaftPass -> shaftPass.draw(target));
        }
    }
}
