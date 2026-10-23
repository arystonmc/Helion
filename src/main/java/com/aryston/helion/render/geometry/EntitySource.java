package com.aryston.helion.render.geometry;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;

public interface EntitySource {
    void prepareLighting();

    boolean supportsGeometryBuffer();

    void renderSolid(RenderPass pass);

    void renderSolidGeometry(RenderPass pass);

    void renderSolidRemainder(RenderPass pass);

    void renderTranslucent(RenderPass pass);

    void renderTranslucentAfterTerrain(RenderPass pass);

    void renderOverlays(RenderTarget target);
}
