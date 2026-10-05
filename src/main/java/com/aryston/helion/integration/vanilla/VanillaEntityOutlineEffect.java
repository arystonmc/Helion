package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.LevelRendererAccessor;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;

final class VanillaEntityOutlineEffect implements RenderStage {
    private static final String NAME = "entity_outline";

    private final LevelRendererAccessor level;

    VanillaEntityOutlineEffect(LevelRendererAccessor level) {
        this.level = level;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return level.helion$currentFrameRendersEntityOutline();
    }

    @Override
    public void addTo(FrameContext frame) {
        PostChain chain = level.helion$shaderManager()
            .getPostChain(LevelRendererAccessor.helion$entityOutlinePostChainId(), LevelTargetBundle.OUTLINE_TARGETS);
        if (chain != null) {
            chain.addToFrame(frame.graph().builder(), frame.scene().width(), frame.scene().height(), level.helion$targets());
        }
    }
}
