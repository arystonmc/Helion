package com.aryston.helion.render.geometry;

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

public final class SolidFeatureStage implements RenderStage {
    private static final String NAME = "solid_features";
    private static final String PASS_LABEL = "Helion Solid Features";

    private final TerrainSource terrain;
    private final EntitySource entities;

    public SolidFeatureStage(TerrainSource terrain, EntitySource entities) {
        this.terrain = terrain;
        this.entities = entities;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return frame.geometry().targets().isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        pass.executes(frame.graph().timed(NAME, () -> render(scene.get())));
    }

    private void render(RenderTarget target) {
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
            terrain.afterOpaque(pass);
            entities.renderSolid(pass);
        }
    }
}
