package com.aryston.helion.render.lightshaft;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;

public interface SceneLightShaftPass {
    void declareReads(FramePass pass);

    void draw(RenderTarget scene);
}
