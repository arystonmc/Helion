package com.aryston.helion.render.water;

public final class RainWetness {
    private static final float SURFACE_WETTING_SECONDS = 30.0F;
    private static final float SURFACE_DRYING_SECONDS = 90.0F;
    private static final float PUDDLE_FILLING_SECONDS = 90.0F;
    private static final float PUDDLE_DRYING_SECONDS = 240.0F;
    private static final double TIME_JUMP_SECONDS = 5.0;
    private static final double SECONDS_PERIOD = 3600.0;
    private static final double NO_TIME = -1.0;

    private float surfaces;
    private float puddles;
    private double lastSeconds = NO_TIME;

    public Wetness update(WeatherState weather) {
        float raining = weather.rainsHere() ? weather.rain() : 0.0F;
        double elapsed = lastSeconds == NO_TIME ? 0.0 : weather.seconds() - lastSeconds;
        lastSeconds = weather.seconds();
        if (elapsed >= 0.0 && elapsed <= TIME_JUMP_SECONDS) {
            float seconds = (float) elapsed;
            surfaces = approach(surfaces, raining, seconds, SURFACE_WETTING_SECONDS, SURFACE_DRYING_SECONDS);
            puddles = approach(puddles, Math.min(raining, surfaces), seconds, PUDDLE_FILLING_SECONDS, PUDDLE_DRYING_SECONDS);
        }
        return new Wetness(surfaces, puddles, raining, (float) (weather.seconds() % SECONDS_PERIOD));
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
