package com.aryston.helion.render.water;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;

public final class RainWetnessStage implements RenderStage {
    private static final String NAME = "rain_wetness";

    private final WeatherSource weather;
    private final WaterResources resources;

    public RainWetnessStage(WeatherSource weather, WaterResources resources) {
        this.weather = weather;
        this.resources = resources;
    }

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
        WeatherState state = weather.weather();
        if (state.underwater()) {
            frame.water().publishUnderwater();
        }
        if (!frame.settings().reflections().rainWetness()) {
            return;
        }
        Wetness wetness = resources.rainWetness().update(state);
        if (wetness.visible()) {
            frame.water().publishWetness(wetness);
        }
    }
}
