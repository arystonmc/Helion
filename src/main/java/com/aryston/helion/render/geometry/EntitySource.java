package com.aryston.helion.render.geometry;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;

public interface EntitySource {
    void prepareLighting();

    void renderSolid(RenderPass pass);

    void renderTranslucent(RenderPass pass);

    void renderTranslucentAfterTerrain(RenderPass pass);

    void renderOverlays(RenderTarget target);
}
