package com.aryston.helion.render.clouds;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;

public interface SceneCloudPass {
    void declareReads(FramePass pass);

    void draw(RenderTarget scene);
}
