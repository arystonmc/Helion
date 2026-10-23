package com.aryston.helion.debug;

import java.util.List;
import net.minecraft.core.BlockPos;

record VisualTestScene(String name, int dayTime, float yaw, float pitch, BlockPos cameraOffset) {
    private static final int SUNRISE = 1500;
    private static final int MORNING = 3000;
    private static final int NOON = 6000;
    private static final int SUNSET = 12000;
    private static final int DUSK = 12700;
    private static final int MOONRISE = 13500;
    private static final int MIDNIGHT = 18000;
    private static final int DAWN = 23500;
    private static final float NORTH = 180.0F;
    private static final float EAST = -90.0F;
    private static final float WEST = 90.0F;
    private static final float CAVE_PITCH = 12.0F;
    private static final float WALL_PITCH = 5.0F;
    private static final float TINT_PITCH = 20.0F;
    private static final float SAVANNA_PITCH = 30.0F;
    private static final float POOL_PITCH = 8.0F;

    static final List<VisualTestScene> ALL = List.of(
        new VisualTestScene("day_sky", NOON, NORTH, -35.0F, BlockPos.ZERO),
        new VisualTestScene("day_sun", SUNRISE, EAST, -20.0F, BlockPos.ZERO),
        new VisualTestScene("day_horizon", NOON, NORTH, 0.0F, BlockPos.ZERO),
        new VisualTestScene("day_showcase", NOON, NORTH, 25.0F, BlockPos.ZERO),
        new VisualTestScene("morning_showcase", MORNING, NORTH, 25.0F, BlockPos.ZERO),
        new VisualTestScene("sunset", SUNSET, WEST, -8.0F, BlockPos.ZERO),
        new VisualTestScene("dusk", DUSK, WEST, -5.0F, BlockPos.ZERO),
        new VisualTestScene("moonrise", MOONRISE, EAST, -20.0F, BlockPos.ZERO),
        new VisualTestScene("night_sky", MIDNIGHT, NORTH, -45.0F, BlockPos.ZERO),
        new VisualTestScene("moon_overhead", MIDNIGHT, NORTH, -88.0F, BlockPos.ZERO),
        new VisualTestScene("night_showcase", MIDNIGHT, NORTH, 25.0F, BlockPos.ZERO),
        new VisualTestScene("dawn_sun", DAWN, EAST, -10.0F, BlockPos.ZERO),
        new VisualTestScene("dawn_away", DAWN, WEST, -10.0F, BlockPos.ZERO),
        new VisualTestScene("water_noon", NOON, NORTH, 15.0F, BlockPos.ZERO),
        new VisualTestScene("water_sunset", SUNSET, NORTH, 15.0F, BlockPos.ZERO),
        new VisualTestScene("water_night", MIDNIGHT, NORTH, 15.0F, BlockPos.ZERO),
        new VisualTestScene("water_mob", NOON, NORTH, POOL_PITCH, VisualTestWorld.POOL_CAMERA),
        new VisualTestScene("cave_corner", MIDNIGHT, NORTH, CAVE_PITCH, VisualTestWorld.CAVE_CAMERA),
        new VisualTestScene("wall_leak", MIDNIGHT, EAST, WALL_PITCH, VisualTestWorld.WALL_CAMERA),
        new VisualTestScene("tinted_glass", MIDNIGHT, EAST, TINT_PITCH, VisualTestWorld.TINT_CAMERA),
        new VisualTestScene("savanna_room", MIDNIGHT, EAST, SAVANNA_PITCH, VisualTestWorld.SAVANNA_CAMERA)
    );
}
