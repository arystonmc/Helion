package com.aryston.helion.render.post;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;

public final class PresentStage implements RenderStage {
    private static final String NAME = "present";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return true;
    }

    @Override
    public void addTo(FrameContext frame) {
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        pass.reads(scene);
        ResourceHandle<RenderTarget> output = pass.readsAndWrites(frame.targets().output());
        frame.targets().updateOutput(output);
        pass.executes(frame.graph().timed(NAME, () -> present(scene.get(), output.get())));
    }

    private static void present(RenderTarget scene, RenderTarget output) {
        output.copyColorFrom(scene);
        output.copyDepthFrom(scene);
    }
}
