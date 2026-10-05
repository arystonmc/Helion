package com.aryston.helion.render.geometry;

import com.mojang.renderpearl.api.commands.RenderPass;

public interface TerrainSource {
    void prepareFrame();

    void renderOpaque(RenderPass pass);

    void renderTranslucent(RenderPass pass);
}
