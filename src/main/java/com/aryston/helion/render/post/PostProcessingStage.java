package com.aryston.helion.render.post;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import java.util.List;

public final class PostProcessingStage implements RenderStage {
    private static final String NAME = "post";

    private final List<RenderStage> effects;

    public PostProcessingStage(List<RenderStage> effects) {
        this.effects = List.copyOf(effects);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return effects.stream().anyMatch(effect -> effect.isActive(frame));
    }

    @Override
    public void addTo(FrameContext frame) {
        effects.stream()
            .filter(effect -> effect.isActive(frame))
            .forEach(effect -> effect.addTo(frame));
    }
}
