package com.aryston.helion.integration.vanilla;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

final class VoxelBiomeTints {
    private static final int BASE_LAYER = 0;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int UNTINTED = RGB_MASK;
    private static final int BYTE_MASK = 0xFF;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int PACKED_BLUE_SHIFT = 16;
    private static final int PACKED_ALPHA_MASK = 0xFF << 24;
    private static final float RED_WEIGHT = 0.2126F;
    private static final float GREEN_WEIGHT = 0.7152F;
    private static final float BLUE_WEIGHT = 0.0722F;
    private static final float MIN_LUMINANCE = 1.0F;

    private final Reference2ObjectOpenHashMap<BlockState, Optional<BlockTintSource>> sources = new Reference2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

    int tinted(int packedAlbedo, BlockState state, BlockAndTintGetter level, int x, int y, int z) {
        Optional<BlockTintSource> source = sources.computeIfAbsent(state, VoxelBiomeTints::source);
        if (source.isEmpty()) {
            return packedAlbedo;
        }
        int tint = source.get().colorInWorld(state, level, position.set(x, y, z)) & RGB_MASK;
        return tint == UNTINTED ? packedAlbedo : withLuminanceOf(tint, packedAlbedo);
    }

    void clear() {
        sources.clear();
    }

    private static Optional<BlockTintSource> source(BlockState state) {
        return Optional.ofNullable(Minecraft.getInstance().getBlockColors().getTintSource(state, BASE_LAYER));
    }

    private static int withLuminanceOf(int tint, int packedAlbedo) {
        int albedoRed = packedAlbedo & BYTE_MASK;
        int albedoGreen = packedAlbedo >> GREEN_SHIFT & BYTE_MASK;
        int albedoBlue = packedAlbedo >> PACKED_BLUE_SHIFT & BYTE_MASK;
        int tintRed = tint >> RED_SHIFT & BYTE_MASK;
        int tintGreen = tint >> GREEN_SHIFT & BYTE_MASK;
        int tintBlue = tint & BYTE_MASK;
        float scale = luminance(albedoRed, albedoGreen, albedoBlue) / Math.max(MIN_LUMINANCE, luminance(tintRed, tintGreen, tintBlue));
        return scaled(tintRed, scale) | scaled(tintGreen, scale) << GREEN_SHIFT | scaled(tintBlue, scale) << PACKED_BLUE_SHIFT
            | packedAlbedo & PACKED_ALPHA_MASK;
    }

    private static float luminance(int red, int green, int blue) {
        return RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue;
    }

    private static int scaled(int channel, float scale) {
        return Math.min(BYTE_MASK, Math.round(channel * scale));
    }
}
