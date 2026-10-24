package com.aryston.helion.render.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RainWetnessTest {
    private static final float RAINING = 1.0F;
    private static final float DRY = 0.0F;
    private static final float NO_FLASH = 0.0F;
    private static final float DESERT = 0.0F;
    private static final float JUNGLE = 0.9F;
    private static final double STEP = 1.0;
    private static final int RAIN_SECONDS = 400;
    private static final int DRYING_SECONDS = 60;
    private static final float TOLERANCE = 1.0e-6F;
    private static final boolean ABOVE_WATER = false;

    @Test
    void surfacesWetBeforePuddlesFill() {
        RainWetness wetness = new RainWetness();
        Wetness after = run(wetness, 0.0, 20, RAINING, JUNGLE);
        assertTrue(after.surfaces() > after.puddles());
    }

    @Test
    void humidClimatesDryMoreSlowly() {
        Wetness desert = dryAfterRain(DESERT);
        Wetness jungle = dryAfterRain(JUNGLE);
        assertTrue(jungle.surfaces() > desert.surfaces());
        assertTrue(jungle.puddles() > desert.puddles());
    }

    @Test
    void noRainHereKeepsSurfacesDry() {
        RainWetness wetness = new RainWetness();
        double seconds = 0.0;
        Wetness last = Wetness.DRY;
        for (int step = 0; step < RAIN_SECONDS; step++) {
            last = wetness.update(new WeatherState(RAINING, seconds, false, NO_FLASH, JUNGLE, ABOVE_WATER));
            seconds += STEP;
        }
        assertEquals(DRY, last.surfaces(), TOLERANCE);
    }

    @Test
    void aTimeJumpKeepsTheWetness() {
        RainWetness wetness = new RainWetness();
        Wetness wet = run(wetness, 0.0, RAIN_SECONDS, RAINING, JUNGLE);
        Wetness jumped = wetness.update(new WeatherState(DRY, RAIN_SECONDS + 10000.0, true, NO_FLASH, JUNGLE, ABOVE_WATER));
        assertEquals(wet.surfaces(), jumped.surfaces(), TOLERANCE);
    }

    private static Wetness dryAfterRain(float humidity) {
        RainWetness wetness = new RainWetness();
        run(wetness, 0.0, RAIN_SECONDS, RAINING, humidity);
        return run(wetness, RAIN_SECONDS, DRYING_SECONDS, DRY, humidity);
    }

    private static Wetness run(RainWetness wetness, double start, int seconds, float rain, float humidity) {
        Wetness last = Wetness.DRY;
        for (int step = 0; step <= seconds; step++) {
            last = wetness.update(new WeatherState(rain, start + step * STEP, true, NO_FLASH, humidity, ABOVE_WATER));
        }
        return last;
    }
}
