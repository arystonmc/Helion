package com.aryston.helion.render.scene;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Objects;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class ClearStage implements RenderStage {
    private static final String NAME = "clear";
    private static final double FAR_DEPTH = 0.0;
    private static final float CLEAR_ALPHA = 0.0F;

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
        Vector4fc fog = frame.scene().fogColor();
        Vector4f clearColor = new Vector4f(fog.x(), fog.y(), fog.z(), CLEAR_ALPHA);
        pass.executes(frame.graph().timed(NAME, () -> clear(scene.get(), clearColor)));
    }

    private static void clear(RenderTarget target, Vector4fc clearColor) {
        RenderSystem.getDevice()
            .createCommandEncoder()
            .clearColorAndDepthTextures(
                Objects.requireNonNull(target.getColorTexture()),
                clearColor,
                Objects.requireNonNull(target.getDepthTexture()),
                FAR_DEPTH
            );
    }
}
