package com.aryston.helion.debug;

import java.util.List;

record VisualTestScene(String name, int dayTime, float yaw, float pitch) {
    private static final int SUNRISE = 1500;
    private static final int NOON = 6000;
    private static final int SUNSET = 12000;
    private static final int MIDNIGHT = 18000;
    private static final float NORTH = 180.0F;
    private static final float EAST = -90.0F;
    private static final float WEST = 90.0F;

    static final List<VisualTestScene> ALL = List.of(
        new VisualTestScene("day_sky", NOON, NORTH, -35.0F),
        new VisualTestScene("day_sun", SUNRISE, EAST, -20.0F),
        new VisualTestScene("day_horizon", NOON, NORTH, 0.0F),
        new VisualTestScene("day_showcase", NOON, NORTH, 25.0F),
        new VisualTestScene("sunset", SUNSET, WEST, -8.0F),
        new VisualTestScene("night_sky", MIDNIGHT, NORTH, -45.0F),
        new VisualTestScene("night_showcase", MIDNIGHT, NORTH, 25.0F)
    );
}
