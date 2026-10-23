package com.aryston.helion.render.atmosphere;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.renderpearl.api.commands.RenderPass;

public interface SceneSkyColor {
    void declareReads(FramePass pass);

    void bind(RenderPass renderPass);
}
