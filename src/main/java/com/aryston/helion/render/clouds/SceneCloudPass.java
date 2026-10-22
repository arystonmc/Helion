package com.aryston.helion.render.clouds;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;

public interface SceneCloudPass {
    ResourceHandle<RenderTarget> resolved();

    void declareReads(FramePass pass);

    void draw(RenderTarget scene);
}
