package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.voxel.VoxelClass;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

final class VoxelBlockClasses {
    private static final byte UNKNOWN = Byte.MIN_VALUE;
    private static final int FULL_DAMPENING = 15;
    private static final int EMISSION_SHIFT = 4;
    private static final int EMISSION_MASK = 0xF;

    private final Reference2ByteOpenHashMap<BlockState> classes = new Reference2ByteOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<BlockState, float[]> colors = new Reference2ObjectOpenHashMap<>();

    VoxelBlockClasses() {
        classes.defaultReturnValue(UNKNOWN);
    }

    byte classify(BlockState state) {
        byte cached = classes.getByte(state);
        if (cached != UNKNOWN || classes.containsKey(state)) {
            return cached;
        }
        byte computed = VoxelClass.encode(occupancy(state), state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        classes.put(state, computed);
        return computed;
    }

    static int emission(byte voxel) {
        return voxel >> EMISSION_SHIFT & EMISSION_MASK;
    }

    float[] color(BlockState state) {
        return colors.computeIfAbsent(state, VoxelLightColors::color);
    }

    void clear() {
        classes.clear();
        colors.clear();
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
