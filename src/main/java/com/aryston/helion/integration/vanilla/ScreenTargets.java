package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.graph.FrameTargets;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;

final class ScreenTargets implements FrameTargets {
    private ResourceHandle<RenderTarget> scene;
    private ResourceHandle<RenderTarget> output;

    ScreenTargets(ResourceHandle<RenderTarget> scene, ResourceHandle<RenderTarget> output) {
        this.scene = scene;
        this.output = output;
    }

    @Override
    public ResourceHandle<RenderTarget> scene() {
        return scene;
    }

    @Override
    public void updateScene(ResourceHandle<RenderTarget> handle) {
        scene = handle;
    }

    @Override
    public void replaceScene(ResourceHandle<RenderTarget> handle) {
        scene = handle;
    }

    @Override
    public ResourceHandle<RenderTarget> output() {
        return output;
    }

    @Override
    public void updateOutput(ResourceHandle<RenderTarget> handle) {
        output = handle;
    }

    @Override
    public void declareGeometryAttachments(FramePass pass) {
    }
}
