package com.aryston.helion.render;

import com.aryston.helion.render.atmosphere.AtmosphereSource;
import com.aryston.helion.render.clouds.CloudSource;
import com.aryston.helion.render.geometry.EntitySource;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.geometry.TransparencySource;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shadow.ShadowCasterSource;
import com.aryston.helion.render.water.FootprintSource;
import com.aryston.helion.render.water.WaveSource;
import com.aryston.helion.render.water.WeatherSource;
import java.util.List;

public record FrameSources(
    TerrainSource terrain,
    EntitySource entities,
    AtmosphereSource atmosphere,
    CloudSource clouds,
    WeatherSource weather,
    WaveSource waves,
    FootprintSource footprints,
    TransparencySource transparency,
    ShadowCasterSource shadowCasters,
    List<RenderStage> postEffects
) {
    public FrameSources {
        postEffects = List.copyOf(postEffects);
    }
}
