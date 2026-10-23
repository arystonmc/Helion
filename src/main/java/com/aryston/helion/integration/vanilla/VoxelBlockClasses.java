package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.voxel.VoxelClass;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

final class VoxelBlockClasses {
    private static final byte UNKNOWN = Byte.MIN_VALUE;
    private static final int FULL_DAMPENING = 15;
    private static final int EMISSION_SHIFT = 4;
    private static final int EMISSION_MASK = 0xF;
    private static final int BYTE_MASK = 0xFF;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int OPAQUE_ALPHA = 0xFF << 24;

    private final Reference2ByteOpenHashMap<BlockState> classes = new Reference2ByteOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<BlockState, float[]> colors = new Reference2ObjectOpenHashMap<>();
    private final Reference2IntOpenHashMap<BlockState> albedos = new Reference2IntOpenHashMap<>();

    VoxelBlockClasses() {
        classes.defaultReturnValue(UNKNOWN);
    }

    byte classify(BlockState state) {
        byte cached = classes.getByte(state);
        if (cached != UNKNOWN || classes.containsKey(state)) {
            return cached;
        }
        byte encoded = VoxelClass.encode(occupancy(state), state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        byte computed = tint(state).isPresent() ? VoxelClass.tinting(encoded) : encoded;
        classes.put(state, computed);
        return computed;
    }

    static int emission(byte voxel) {
        return voxel >> EMISSION_SHIFT & EMISSION_MASK;
    }

    int albedo(BlockState state) {
        return albedos.computeIfAbsent(state, (BlockState key) -> packed(tint(key).orElseGet(() -> key.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col)));
    }

    float[] color(BlockState state) {
        return colors.computeIfAbsent(state, VoxelLightColors::color);
    }

    void clear() {
        classes.clear();
        colors.clear();
        albedos.clear();
    }

    private static int packed(int rgb) {
        int red = rgb >> RED_SHIFT & BYTE_MASK;
        int green = rgb >> GREEN_SHIFT & BYTE_MASK;
        int blue = rgb & BYTE_MASK;
        return red | green << GREEN_SHIFT | blue << RED_SHIFT | OPAQUE_ALPHA;
    }

    private static Optional<Integer> tint(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof StainedGlassBlock glass) {
            return Optional.of(brightest(glass.getColor().getTextureDiffuseColor()));
        }
        if (block instanceof StainedGlassPaneBlock pane) {
            return Optional.of(brightest(pane.getColor().getTextureDiffuseColor()));
        }
        return Optional.empty();
    }

    private static int brightest(int rgb) {
        int red = rgb >> RED_SHIFT & BYTE_MASK;
        int green = rgb >> GREEN_SHIFT & BYTE_MASK;
        int blue = rgb & BYTE_MASK;
        int peak = Math.max(1, Math.max(red, Math.max(green, blue)));
        return scaled(red, peak) << RED_SHIFT | scaled(green, peak) << GREEN_SHIFT | scaled(blue, peak);
    }

    private static int scaled(int channel, int peak) {
        return channel * BYTE_MASK / peak;
    }

    private static byte occupancy(BlockState state) {
        if (state.isAir()) {
            return VoxelClass.EMPTY;
        }
        if (state.isSolidRender() || state.getLightDampening() >= FULL_DAMPENING) {
            return VoxelClass.OPAQUE;
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty() && fluid.is(FluidTags.WATER)) {
            return VoxelClass.TRANSLUCENT;
        }
        if (state.getRenderShape() != RenderShape.MODEL) {
            return fluid.isEmpty() ? VoxelClass.EMPTY : VoxelClass.PARTIAL;
        }
        return hasTranslucentModel(state) ? VoxelClass.TRANSLUCENT : VoxelClass.PARTIAL;
    }

    private static boolean hasTranslucentModel(BlockState state) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state)
            .hasMaterialFlag(BlockAndTintGetter.EMPTY, BlockPos.ZERO, state, BakedQuad.FLAG_TRANSLUCENT);
    }
}
