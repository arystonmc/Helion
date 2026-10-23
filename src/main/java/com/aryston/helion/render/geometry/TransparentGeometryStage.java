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

    public TransparentGeometryStage(TerrainSource terrain, EntitySource entities, AtmosphereSource atmosphere, TransparencySource transparency) {
        this.terrain = terrain;
        this.entities = entities;
        this.atmosphere = atmosphere;
        this.transparency = transparency;
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
        pass.executes(frame.graph().timed(NAME, () -> render(scene.get(), orderIndependent, atmospherePasses)));
    }

    private void render(RenderTarget target, boolean orderIndependent, AtmospherePasses atmospherePasses) {
        if (orderIndependent) {
            transparency.renderOrderIndependent();
            atmospherePasses.draw(target);
        } else if (!atmospherePasses.isEmpty()) {
            renderSortedThroughFog(target, atmospherePasses);
        } else {
            renderSorted(target);
        }
        entities.renderOverlays(target);
    }

    private void renderSorted(RenderTarget target) {
        try (RenderPass pass = openPass(target)) {
            renderTranslucents(pass);
            renderAtmosphere(pass);
        }
    }

    private void renderSortedThroughFog(RenderTarget target, AtmospherePasses atmospherePasses) {
        try (RenderPass pass = openPass(target)) {
            renderTranslucents(pass);
        }
        atmospherePasses.draw(target);
        try (RenderPass pass = openPass(target)) {
            renderAtmosphere(pass);
        }
    }

    private void renderTranslucents(RenderPass pass) {
        entities.renderTranslucent(pass);
        terrain.renderTranslucent(pass);
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
