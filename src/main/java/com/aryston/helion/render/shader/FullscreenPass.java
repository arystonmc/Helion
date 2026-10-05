package com.aryston.helion.render.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class FullscreenPass {
    private static final int TRIANGLE_VERTICES = 3;
    private static final int SINGLE_INSTANCE = 1;
    private static final int FIRST = 0;

    private FullscreenPass() {
    }

    public static void draw(String label, RenderTarget target, CompiledRenderPipeline pipeline, Consumer<RenderPass> bindings) {
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> label, Objects.requireNonNull(target.getColorTextureView()), Optional.empty())) {
            bindings.accept(pass);
            pass.setPipeline(pipeline);
            pass.draw(TRIANGLE_VERTICES, SINGLE_INSTANCE, FIRST, FIRST);
        }
    }
}
