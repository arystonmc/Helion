package com.aryston.helion.render.atmosphere;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;

public interface SceneFogPass {
    void declareReads(FramePass pass);

    void draw(RenderTarget scene);
}
