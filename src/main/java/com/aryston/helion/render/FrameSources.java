package com.aryston.helion.render;

import com.aryston.helion.render.atmosphere.AtmosphereSource;
import com.aryston.helion.render.geometry.EntitySource;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.geometry.TransparencySource;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shadow.ShadowCasterSource;
import java.util.List;

public record FrameSources(
    TerrainSource terrain,
    EntitySource entities,
    AtmosphereSource atmosphere,
    TransparencySource transparency,
    ShadowCasterSource shadowCasters,
    List<RenderStage> postEffects
) {
    public FrameSources {
        postEffects = List.copyOf(postEffects);
    }
}
