package com.aryston.helion.render;

import com.aryston.helion.render.atmosphere.AtmosphereSource;
import com.aryston.helion.render.geometry.EntitySource;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.geometry.TransparencySource;
import com.aryston.helion.render.graph.RenderStage;
import java.util.List;

public record FrameSources(
    TerrainSource terrain,
    EntitySource entities,
    AtmosphereSource atmosphere,
    TransparencySource transparency,
    List<RenderStage> postEffects
) {
    public FrameSources {
        postEffects = List.copyOf(postEffects);
    }
}
