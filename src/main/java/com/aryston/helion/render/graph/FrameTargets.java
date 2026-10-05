package com.aryston.helion.render.graph;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;

public interface FrameTargets {
    ResourceHandle<RenderTarget> scene();

    void updateScene(ResourceHandle<RenderTarget> handle);

    ResourceHandle<RenderTarget> output();

    void updateOutput(ResourceHandle<RenderTarget> handle);

    void declareGeometryAttachments(FramePass pass);
}
