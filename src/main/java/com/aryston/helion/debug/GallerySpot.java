package com.aryston.helion.debug;

import java.util.List;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;

record GallerySpot(String name, GalleryLocator locator, int dayTime, List<Integer> yaws, float pitch, int heightAboveSurface) {
    private static final int MORNING = 1000;
    private static final int NOON = 6000;
    private static final int AFTERNOON = 11500;
    private static final int EVENING = 12000;
    private static final int SUNSET = 12500;
    private static final int MIDNIGHT = 18000;
    private static final List<Integer> ALL_DIRECTIONS = List.of(0, 90, 180, 270);
    private static final List<Integer> SHORE_DIRECTIONS = List.of(90, 0, 180);
    private static final GalleryLocator PLAINS_VILLAGE = GalleryLocator.structure(BuiltinStructures.VILLAGE_PLAINS);

    static final List<GallerySpot> ALL = List.of(
        new GallerySpot("cherry", GalleryLocator.biome(List.of(Biomes.CHERRY_GROVE)), MORNING, ALL_DIRECTIONS, 8.0F, 6),
        new GallerySpot("village_day", PLAINS_VILLAGE, NOON, ALL_DIRECTIONS, 15.0F, 14),
        new GallerySpot("village_night", PLAINS_VILLAGE, MIDNIGHT, ALL_DIRECTIONS, 15.0F, 10),
        new GallerySpot("peaks", GalleryLocator.biome(List.of(Biomes.JAGGED_PEAKS, Biomes.MEADOW)), EVENING, ALL_DIRECTIONS, 5.0F, 8),
        new GallerySpot("ocean", GalleryLocator.biome(List.of(Biomes.BEACH)), SUNSET, SHORE_DIRECTIONS, 3.0F, 4),
        new GallerySpot("forest", GalleryLocator.biome(List.of(Biomes.DARK_FOREST)), NOON, ALL_DIRECTIONS, 20.0F, 3),
        new GallerySpot("savanna", GalleryLocator.biome(List.of(Biomes.SAVANNA)), AFTERNOON, ALL_DIRECTIONS, 5.0F, 8)
    );
}
