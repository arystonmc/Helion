package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.LevelRendererAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.SkyRenderer;
import org.jspecify.annotations.Nullable;

final class SceneSkyRenderer {
    private @Nullable SkyRenderer renderer;
    private @Nullable RenderTarget boundTarget;

    SkyRenderer obtain(LevelRendererAccessor level, RenderTarget scene, boolean reset) {
        if (renderer == null || reset || boundTarget != scene) {
            close();
            renderer = new SkyRenderer(level.helion$textureManager(), level.helion$atlasManager(), scene);
            boundTarget = scene;
        }
        return renderer;
    }

    void close() {
        if (renderer != null) {
            renderer.close();
        }
        renderer = null;
        boundTarget = null;
    }
}
