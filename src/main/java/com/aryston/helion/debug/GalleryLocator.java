package com.aryston.helion.debug;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

@FunctionalInterface
interface GalleryLocator {
    int BIOME_SEARCH_RADIUS = 6400;
    int BIOME_SAMPLE_HORIZONTAL = 32;
    int BIOME_SAMPLE_VERTICAL = 64;
    int STRUCTURE_SEARCH_RADIUS_CHUNKS = 100;

    Optional<BlockPos> locate(ServerLevel level, BlockPos origin);

    static GalleryLocator biome(List<ResourceKey<Biome>> biomes) {
        return (level, origin) -> {
            Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(
                holder -> biomes.stream().anyMatch(holder::is), origin, BIOME_SEARCH_RADIUS, BIOME_SAMPLE_HORIZONTAL, BIOME_SAMPLE_VERTICAL
            );
            return found == null ? Optional.empty() : Optional.of(found.getFirst());
        };
    }

    static GalleryLocator structure(ResourceKey<Structure> structure) {
        return (level, origin) -> {
            Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(structure);
            return Optional.ofNullable(level.findNearestMapStructure(HolderSet.direct(holder), origin, STRUCTURE_SEARCH_RADIUS_CHUNKS, false));
        };
    }
}
