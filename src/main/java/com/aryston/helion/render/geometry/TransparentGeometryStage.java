package com.aryston.helion.render.geometry;

import com.aryston.helion.render.atmosphere.AtmosphereSource;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
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
        boolean orderIndependent = frame.scene().orderIndependentTransparency();
        pass.executes(frame.graph().timed(NAME, () -> render(scene.get(), orderIndependent)));
    }

    private void render(RenderTarget target, boolean orderIndependent) {
        if (orderIndependent) {
            transparency.renderOrderIndependent();
        } else {
            renderSorted(target);
        }
        entities.renderOverlays(target);
    }

    private void renderSorted(RenderTarget target) {
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                    () -> PASS_LABEL,
                    Objects.requireNonNull(target.getColorTextureView()),
                    Optional.empty(),
                    target.getDepthTextureView(),
                    OptionalDouble.empty()
                )) {
            RenderSystem.bindDefaultUniforms(pass);
            entities.renderTranslucent(pass);
            terrain.renderTranslucent(pass);
            entities.renderTranslucentAfterTerrain(pass);
            atmosphere.renderClouds(pass);
            atmosphere.renderWeather(pass);
            atmosphere.renderWorldBorder(pass);
        }
    }
}
