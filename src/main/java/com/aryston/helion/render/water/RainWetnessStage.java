package com.aryston.helion.render.water;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import java.util.List;

public final class RainWetnessStage implements RenderStage {
    private static final String NAME = "rain_wetness";

    private final WeatherSource weather;
    private final FootprintSource footprints;
    private final WaterResources resources;

    public RainWetnessStage(WeatherSource weather, FootprintSource footprints, WaterResources resources) {
        this.weather = weather;
        this.footprints = footprints;
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
        weather.extendUnderwaterView(frame.settings().reflections().realisticWater());
        if (state.lavaNearby()) {
            frame.water().publishLava();
        }
        if (state.underwater().submerged()) {
            frame.water().publishUnderwater(state.underwater());
        }
        if (!frame.settings().reflections().rainWetness()) {
            resources.footprints().clear();
            return;
        }
        resources.footprints().add(footprints.steps(frame.camera().position().x(), frame.camera().position().z(), state.seconds()));
        List<Footprint> visible = resources.footprints().visible(state.seconds());
        if (!visible.isEmpty()) {
            frame.water().publishFootprints(visible, state.seconds());
        }
        Wetness wetness = resources.rainWetness().update(state);
        if (wetness.visible()) {
            frame.water().publishWetness(wetness);
        }
    }
}
