package com.aryston.helion.render.geometry;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;

public interface TerrainSource {
    void prepareFrame();

    boolean supportsGeometryBuffer();

    void renderOpaque(RenderPass pass);

    void renderOpaqueGeometry(RenderPass geometryPass, boolean seeThroughGlass);

    void afterOpaque(RenderPass pass);

    void renderTranslucent(RenderPass pass, Optional<GpuTextureView> seeThroughGlass);

    boolean supportsWaterSurface();

    void renderWaterSurface(RenderPass waterPass);

    TerrainTextures textures();

    GlossyCells glossyCells();
}
