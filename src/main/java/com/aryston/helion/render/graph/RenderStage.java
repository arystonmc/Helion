package com.aryston.helion.render.graph;

public interface RenderStage {
    String name();

    boolean isActive(FrameContext frame);

    void addTo(FrameContext frame);
}
