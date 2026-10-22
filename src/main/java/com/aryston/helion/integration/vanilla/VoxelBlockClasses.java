package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.voxel.VoxelClass;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
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
    private final Reference2ByteOpenHashMap<BlockState> classes = new Reference2ByteOpenHashMap<>();

    byte classify(BlockState state) {
        byte cached = classes.getOrDefault(state, Byte.MIN_VALUE);
        if (cached != Byte.MIN_VALUE || classes.containsKey(state)) {
            return cached;
        }
        byte computed = VoxelClass.encode(occupancy(state), state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        classes.put(state, computed);
        return computed;
    }

    void clear() {
        classes.clear();
    }

    private static byte occupancy(BlockState state) {
        if (state.isAir()) {
            return VoxelClass.EMPTY;
        }
        if (state.isSolidRender()) {
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
