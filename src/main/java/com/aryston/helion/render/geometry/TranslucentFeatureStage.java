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

public final class TranslucentFeatureStage implements RenderStage {
    private static final String NAME = "translucent_features";
    private static final String PASS_LABEL = "Helion Translucent Features";

    private final EntitySource entities;

    public TranslucentFeatureStage(EntitySource entities) {
        this.entities = entities;
    }

    static boolean drawsBeforeWater(FrameContext frame) {
        boolean reflectsWater = frame.settings().reflections().realisticWater()
            || frame.settings().reflections().screenSpace()
            || frame.settings().rayTracing().reflectsWater();
        return reflectsWater && !frame.scene().orderIndependentTransparency();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return drawsBeforeWater(frame);
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
            entities.renderTranslucent(pass);
        }
    }
}
