package com.aryston.helion.render.water;

public final class RainWetness {
    private static final float SURFACE_WETTING_SECONDS = 60.0F;
    private static final float SURFACE_DRYING_SECONDS = 90.0F;
    private static final float PUDDLE_FILLING_SECONDS = 150.0F;
    private static final float PUDDLE_DRYING_SECONDS = 240.0F;
    private static final double TIME_JUMP_SECONDS = 5.0;
    private static final double SECONDS_PERIOD = 3600.0;
    private static final double NO_TIME = -1.0;
    private static final float DRY_CLIMATE_DRYING = 0.4F;
    private static final float HUMID_CLIMATE_DRYING = 2.0F;

    private float surfaces;
    private float puddles;
    private double lastSeconds = NO_TIME;

    public Wetness update(WeatherState weather) {
        float raining = weather.rainsHere() ? weather.rain() : 0.0F;
        double elapsed = lastSeconds == NO_TIME ? 0.0 : weather.seconds() - lastSeconds;
        lastSeconds = weather.seconds();
        if (elapsed >= 0.0 && elapsed <= TIME_JUMP_SECONDS) {
            float seconds = (float) elapsed;
            float drying = DRY_CLIMATE_DRYING + (HUMID_CLIMATE_DRYING - DRY_CLIMATE_DRYING) * Math.clamp(weather.humidity(), 0.0F, 1.0F);
            surfaces = approach(surfaces, raining, seconds, SURFACE_WETTING_SECONDS, SURFACE_DRYING_SECONDS * drying);
            puddles = approach(puddles, Math.min(raining, surfaces), seconds, PUDDLE_FILLING_SECONDS, PUDDLE_DRYING_SECONDS * drying);
        }
        return new Wetness(surfaces, puddles, raining, (float) (weather.seconds() % SECONDS_PERIOD), weather.flash());
    }

    public void saturate() {
        surfaces = 1.0F;
        puddles = 1.0F;
    }

    private static float approach(float current, float target, float seconds, float risingSeconds, float fallingSeconds) {
        if (target > current) {
            return Math.min(target, current + seconds / risingSeconds);
        }
        return Math.max(target, current - seconds / fallingSeconds);
    }
}
