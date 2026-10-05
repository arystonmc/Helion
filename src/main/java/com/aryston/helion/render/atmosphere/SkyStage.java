package com.aryston.helion.render.atmosphere;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;

public final class SkyStage implements RenderStage {
    private static final String NAME = "sky";

    private final AtmosphereSource atmosphere;

    public SkyStage(AtmosphereSource atmosphere) {
        this.atmosphere = atmosphere;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return frame.scene().renderSky() && atmosphere.hasSky();
    }

    @Override
    public void addTo(FrameContext frame) {
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        pass.executes(frame.graph().timed(NAME, () -> atmosphere.renderSky(scene.get())));
    }
}
