package com.aryston.helion.render.water;

public interface WeatherSource {
    WeatherState weather();

    void extendUnderwaterView(boolean extend);
}
