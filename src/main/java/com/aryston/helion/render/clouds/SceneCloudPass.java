package com.aryston.helion.render.clouds;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.Optional;

public interface SceneCloudPass {
    ResourceHandle<RenderTarget> resolved();

    Optional<ResourceHandle<RenderTarget>> skyMap();

    void declareReads(FramePass pass);

    void draw(RenderTarget scene);
}
