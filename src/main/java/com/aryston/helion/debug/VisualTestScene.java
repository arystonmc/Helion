package com.aryston.helion.debug;

import java.util.List;
import net.minecraft.core.BlockPos;

record VisualTestScene(String name, int dayTime, float yaw, float pitch, BlockPos cameraOffset, boolean rain, boolean wet) {
    private static final int EARLY_MORNING = 300;
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
    private static final float SOUTH = 0.0F;
    private static final float NORTH_WEST = 135.0F;
    private static final float HORIZON_PITCH = 6.0F;
    private static final float HIGH_PITCH = 12.0F;
    private static final BlockPos HIGH_ABOVE_START = new BlockPos(0, 15, 0);
    private static final float CAVE_PITCH = 12.0F;
    private static final float WALL_PITCH = 5.0F;
    private static final float TINT_PITCH = 20.0F;
    private static final float SAVANNA_PITCH = 30.0F;
    private static final float POOL_PITCH = 8.0F;
    private static final float LAKE_PITCH = 12.0F;
    private static final float GRASS_PITCH = 50.0F;
    private static final float LAVA_PITCH = 25.0F;
    private static final float LAKE_FLOOR_PITCH = 20.0F;
    private static final float CLOSE_PITCH = 45.0F;
    private static final float UNDERWATER_PITCH = -40.0F;
    private static final float SHOWCASE_PITCH = 25.0F;
    private static final float RAIN_POOL_PITCH = 35.0F;
    private static final boolean RAIN = true;
    private static final boolean CLEAR = false;
    private static final boolean WET = true;
    private static final String FOOTPRINT_SCENE = "footprints";
    private static final String MOVING_SUFFIX = "_moving";
    private static final String CHANGE_SCENE = "torch_room_change";
    private static final float RAINBOW_PITCH = -15.0F;
    private static final float OCEAN_PITCH = 10.0F;
    private static final float GLITTER_PITCH = 6.0F;
    private static final float OCEAN_UNDER_PITCH = -8.0F;
    private static final float SWAMP_PITCH = 40.0F;
    private static final float BEACH_PITCH = 45.0F;
    private static final float RIVER_PITCH = 15.0F;
    private static final float AWAY_PITCH = 30.0F;
    private static final float SHORE_PITCH = 25.0F;
    private static final float GLASS_PITCH = 3.0F;
    private static final float STAINED_LIGHT_PITCH = 50.0F;
    private static final float GLASS_LAKE_PITCH = 35.0F;
    private static final float GLASS_ABOVE_PITCH = 60.0F;
    private static final float EMITTER_PITCH = 25.0F;
    private static final float WATERFALL_PITCH = -5.0F;
    private static final float TORCH_ROOM_PITCH = 10.0F;
    private static final float WATERFALL_ABOVE_PITCH = 35.0F;
    private static final float SWAMP_UNDER_PITCH = 15.0F;
    private static final float PANE_ROOM_PITCH = 10.0F;
    private static final float LEAK_PITCH = 30.0F;
    private static final float LAVA_CAVE_PITCH = 25.0F;
    private static final float DYNAMIC_PITCH = 20.0F;

    static final List<VisualTestScene> ALL = List.of(
        new VisualTestScene("day_sky", NOON, NORTH, -35.0F, BlockPos.ZERO),
        new VisualTestScene("day_sun", SUNRISE, EAST, -20.0F, BlockPos.ZERO),
        new VisualTestScene("day_horizon", NOON, NORTH, 0.0F, BlockPos.ZERO),
        new VisualTestScene("horizon_east", NOON, EAST, HORIZON_PITCH, BlockPos.ZERO),
        new VisualTestScene("horizon_south", NOON, SOUTH, HORIZON_PITCH, BlockPos.ZERO),
        new VisualTestScene("horizon_west", NOON, WEST, HORIZON_PITCH, BlockPos.ZERO),
        new VisualTestScene("horizon_high_north", NOON, NORTH, HIGH_PITCH, HIGH_ABOVE_START),
        new VisualTestScene("horizon_high_east", NOON, EAST, HIGH_PITCH, HIGH_ABOVE_START),
        new VisualTestScene("moonlit_terrain", MOONRISE, WEST, HIGH_PITCH, HIGH_ABOVE_START),
        new VisualTestScene("night_terrain", MIDNIGHT, NORTH, HIGH_PITCH, HIGH_ABOVE_START),
        new VisualTestScene("horizon_high_south", NOON, SOUTH, HIGH_PITCH, HIGH_ABOVE_START),
        new VisualTestScene("horizon_high_west", NOON, WEST, HIGH_PITCH, HIGH_ABOVE_START),
        new VisualTestScene("day_showcase", NOON, NORTH, 25.0F, BlockPos.ZERO),
        new VisualTestScene("day_showcase_moving", NOON, NORTH, 25.0F, BlockPos.ZERO),
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
        new VisualTestScene("water_close", NOON, NORTH, CLOSE_PITCH, VisualTestWorld.POOL_CAMERA),
        new VisualTestScene("water_glint", MORNING, EAST, CLOSE_PITCH, VisualTestWorld.POOL_SIDE_CAMERA),
        new VisualTestScene("water_night_close", MIDNIGHT, NORTH, CLOSE_PITCH, VisualTestWorld.POOL_CAMERA),
        new VisualTestScene("water_lake", NOON, NORTH, LAKE_PITCH, VisualTestWorld.LAKE_CAMERA),
        new VisualTestScene("water_lake_sunset", SUNSET, NORTH, LAKE_PITCH, VisualTestWorld.LAKE_CAMERA),
        new VisualTestScene("water_lake_night", MIDNIGHT, NORTH, LAKE_PITCH, VisualTestWorld.LAKE_CAMERA),
        new VisualTestScene("water_lake_under", NOON, NORTH, UNDERWATER_PITCH, VisualTestWorld.LAKE_UNDER_CAMERA),
        new VisualTestScene("water_lake_under_floor", NOON, NORTH, LAKE_FLOOR_PITCH, VisualTestWorld.LAKE_UNDER_CAMERA),
        new VisualTestScene("water_lake_under_night", MIDNIGHT, NORTH, UNDERWATER_PITCH, VisualTestWorld.LAKE_UNDER_CAMERA),
        new VisualTestScene("water_lake_storm", NOON, NORTH, LAKE_PITCH, VisualTestWorld.LAKE_CAMERA, RAIN),
        new VisualTestScene("water_ocean", NOON, EAST, OCEAN_PITCH, VisualTestWorld.OCEAN_CAMERA),
        new VisualTestScene("water_ocean_glitter", SUNRISE, EAST, GLITTER_PITCH, VisualTestWorld.OCEAN_CAMERA),
        new VisualTestScene("water_ocean_under", MORNING, EAST, OCEAN_UNDER_PITCH, VisualTestWorld.OCEAN_UNDER_CAMERA),
        new VisualTestScene("water_ocean_under_away", MORNING, WEST, AWAY_PITCH, VisualTestWorld.OCEAN_UNDER_CAMERA),
        new VisualTestScene("water_ocean_shore", NOON, EAST, SHORE_PITCH, VisualTestWorld.OCEAN_SHORE_CAMERA),
        new VisualTestScene("glass_window", NOON, NORTH, GLASS_PITCH, VisualTestWorld.GLASS_CAMERA),
        new VisualTestScene("glass_window_rain", NOON, NORTH, GLASS_PITCH, VisualTestWorld.GLASS_CAMERA, RAIN),
        new VisualTestScene("glass_window_rain_above", NOON, NORTH, GLASS_ABOVE_PITCH, VisualTestWorld.GLASS_ABOVE_CAMERA, RAIN),
        new VisualTestScene("glass_lake_rain", DUSK, NORTH, GLASS_LAKE_PITCH, VisualTestWorld.LAKE_CAMERA, RAIN),
        new VisualTestScene("emitter_shadow", SUNRISE, NORTH, EMITTER_PITCH, VisualTestWorld.EMITTER_CAMERA),
        new VisualTestScene("waterfall_close", NOON, NORTH, WATERFALL_PITCH, VisualTestWorld.WATERFALL_CAMERA),
        new VisualTestScene("waterfall_above", NOON, NORTH_WEST, WATERFALL_ABOVE_PITCH, VisualTestWorld.WATERFALL_ABOVE_CAMERA),
        new VisualTestScene("emitter_night", MIDNIGHT, NORTH, EMITTER_PITCH, VisualTestWorld.EMITTER_CAMERA),
        new VisualTestScene("torch_room", MIDNIGHT, NORTH_WEST, TORCH_ROOM_PITCH, VisualTestWorld.TORCH_ROOM_CAMERA),
        new VisualTestScene("torch_room_moving", MIDNIGHT, NORTH_WEST, TORCH_ROOM_PITCH, VisualTestWorld.TORCH_ROOM_CAMERA),
        new VisualTestScene("torch_room_change", MIDNIGHT, NORTH_WEST, TORCH_ROOM_PITCH, VisualTestWorld.TORCH_ROOM_CAMERA),
        new VisualTestScene("water_swamp", NOON, NORTH, SWAMP_PITCH, VisualTestWorld.SWAMP_CAMERA),
        new VisualTestScene("water_swamp_under", NOON, NORTH, SWAMP_UNDER_PITCH, VisualTestWorld.SWAMP_UNDER_CAMERA),
        new VisualTestScene("water_beach", NOON, NORTH, BEACH_PITCH, VisualTestWorld.BEACH_CAMERA),
        new VisualTestScene("water_river", NOON, NORTH, RIVER_PITCH, VisualTestWorld.RIVER_CAMERA),
        new VisualTestScene("water_under", NOON, NORTH, UNDERWATER_PITCH, VisualTestWorld.UNDERWATER_CAMERA),
        new VisualTestScene("water_under_floor", NOON, NORTH, CLOSE_PITCH, VisualTestWorld.UNDERWATER_CAMERA),
        new VisualTestScene("cave_corner", MIDNIGHT, NORTH, CAVE_PITCH, VisualTestWorld.CAVE_CAMERA),
        new VisualTestScene("cave_noon", NOON, NORTH, CAVE_PITCH, VisualTestWorld.CAVE_CAMERA),
        new VisualTestScene("wall_leak", MIDNIGHT, EAST, WALL_PITCH, VisualTestWorld.WALL_CAMERA),
        new VisualTestScene("tinted_glass", MIDNIGHT, EAST, TINT_PITCH, VisualTestWorld.TINT_CAMERA),
        new VisualTestScene("stained_light", MIDNIGHT, NORTH, STAINED_LIGHT_PITCH, VisualTestWorld.STAINED_LIGHT_CAMERA),
        new VisualTestScene("stained_light_rain", MIDNIGHT, NORTH, STAINED_LIGHT_PITCH, VisualTestWorld.STAINED_LIGHT_CAMERA, RAIN),
        new VisualTestScene("glass_pane_room_rain", MIDNIGHT, NORTH_WEST, PANE_ROOM_PITCH, VisualTestWorld.PANE_ROOM_CAMERA, RAIN),
        new VisualTestScene("corner_leak", MIDNIGHT, NORTH, LEAK_PITCH, VisualTestWorld.LEAK_CAMERA),
        new VisualTestScene("lava_cave", MIDNIGHT, NORTH, LAVA_CAVE_PITCH, VisualTestWorld.LAVA_CAVE_CAMERA),
        new VisualTestScene("dynamic_lights", MIDNIGHT, NORTH, DYNAMIC_PITCH, VisualTestWorld.DYNAMIC_CAMERA),
        new VisualTestScene("savanna_room", MIDNIGHT, EAST, SAVANNA_PITCH, VisualTestWorld.SAVANNA_CAMERA),
        new VisualTestScene("rain_yard", NOON, NORTH, SHOWCASE_PITCH, VisualTestWorld.RAIN_CAMERA, RAIN),
        new VisualTestScene("rain_yard_low", NOON, NORTH, POOL_PITCH, VisualTestWorld.RAIN_CAMERA, RAIN),
        new VisualTestScene("rain_yard_dusk", SUNSET, NORTH, POOL_PITCH, VisualTestWorld.RAIN_CAMERA, RAIN),
        new VisualTestScene("rain_pool", NOON, NORTH, RAIN_POOL_PITCH, VisualTestWorld.RAIN_POOL_CAMERA, RAIN),
        new VisualTestScene("grass_close", NOON, EAST, GRASS_PITCH, VisualTestWorld.GRASS_CAMERA),
        new VisualTestScene("grass_close_morning", MORNING, EAST, GRASS_PITCH, VisualTestWorld.GRASS_CAMERA),
        new VisualTestScene("grass_close_rain", NOON, EAST, GRASS_PITCH, VisualTestWorld.GRASS_CAMERA, RAIN),
        new VisualTestScene("lava_pool", NOON, NORTH, LAVA_PITCH, VisualTestWorld.LAVA_CAMERA),
        new VisualTestScene("lava_pool_night", MIDNIGHT, NORTH, LAVA_PITCH, VisualTestWorld.LAVA_CAMERA),
        new VisualTestScene("footprints", NOON, NORTH, CLOSE_PITCH, VisualTestWorld.POOL_CAMERA),
        new VisualTestScene("rainbow", EARLY_MORNING, WEST, RAINBOW_PITCH, VisualTestWorld.RAIN_CAMERA, CLEAR, WET)
    );

    VisualTestScene(String name, int dayTime, float yaw, float pitch, BlockPos cameraOffset, boolean rain) {
        this(name, dayTime, yaw, pitch, cameraOffset, rain, rain);
    }

    boolean walks() {
        return FOOTPRINT_SCENE.equals(name);
    }

    boolean moves() {
        return name.endsWith(MOVING_SUFFIX);
    }

    boolean changesBlocks() {
        return CHANGE_SCENE.equals(name);
    }

    VisualTestScene(String name, int dayTime, float yaw, float pitch, BlockPos cameraOffset) {
        this(name, dayTime, yaw, pitch, cameraOffset, CLEAR, CLEAR);
    }
}
