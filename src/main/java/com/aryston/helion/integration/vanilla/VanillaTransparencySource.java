package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.LevelRendererAccessor;
import com.aryston.helion.render.geometry.TransparencySource;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

final class VanillaTransparencySource implements TransparencySource {
    private final LevelRendererAccessor level;
    private final ChunkSectionsToRender sections;
    private final FeatureRenderDispatcher.PreparedFrame featureFrame;

    VanillaTransparencySource(LevelFrameRequest request, ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame featureFrame) {
        this.level = request.level();
        this.sections = sections;
        this.featureFrame = featureFrame;
    }

    @Override
    public void renderOrderIndependent() {
        level.helion$executeOit(sections, featureFrame);
    }
}
