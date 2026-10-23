package com.aryston.helion.render.water;

public record WeatherState(float rain, double seconds, boolean rainsHere) {
    public static final WeatherState DRY = new WeatherState(0.0F, 0.0, false);
}
