package com.aryston.helion.render.water;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;

public interface SceneReflectionPass {
    void declareReads(FramePass pass);

    void draw(RenderTarget scene);
}
