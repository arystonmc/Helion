package com.aryston.helion.render.geometry;

import com.mojang.renderpearl.api.commands.RenderPass;

public interface TerrainSource {
    void prepareFrame();

    boolean supportsGeometryBuffer();

    void renderOpaque(RenderPass pass);

    void renderOpaqueGeometry(RenderPass geometryPass);

    void afterOpaque(RenderPass pass);

    void renderTranslucent(RenderPass pass);
}
