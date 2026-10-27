package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.rtx.RtxSectionUpdate;
import java.util.Arrays;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

final class RtxSectionLights {
    private static final int SECTION_SIZE = 16;
    private static final int MAX_LIGHTS = 32;
    private static final int NO_LIGHT = 0;
    private static final float MAX_LEVEL = 15.0F;
    private static final float RANGE_MARGIN = 0.5F;
    private static final double LIGHT_GAMMA = 2.2;
    private static final int HALF_MASK = 0xFFFF;
    private static final int HIGH_SHIFT = 16;
    private static final int MIN_X = 0;
    private static final int MIN_Y = 1;
    private static final int MIN_Z = 2;
    private static final int COLOR = 3;
    private static final int MAX_X = 4;
    private static final int MAX_Y = 5;
    private static final int MAX_Z = 6;
    private static final int LEVEL = 7;
    private static final AABB FULL_BLOCK = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    private RtxSectionLights() {
    }

    static int[] scan(SectionPos position, RenderSectionRegion region) {
        int[] found = new int[RtxSectionUpdate.LIGHT_INTS * MAX_LIGHTS];
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = 0; y < SECTION_SIZE; y++) {
            for (int z = 0; z < SECTION_SIZE; z++) {
                for (int x = 0; x < SECTION_SIZE; x++) {
                    cursor.set(position.minBlockX() + x, position.minBlockY() + y, position.minBlockZ() + z);
                    BlockState state = region.getBlockState(cursor);
                    int level = state.getLightEmission(region, cursor);
                    if (level == NO_LIGHT) {
                        continue;
                    }
                    if (count == MAX_LIGHTS) {
                        found = Arrays.copyOf(found, found.length * 2);
                    }
                    write(found, count++, x, y, z, state, region, cursor, level);
                }
            }
        }
        return count > MAX_LIGHTS ? thinned(found, count) : Arrays.copyOf(found, count * RtxSectionUpdate.LIGHT_INTS);
    }

    private static void write(int[] lights, int index, int x, int y, int z, BlockState state, RenderSectionRegion region, BlockPos cursor, int level) {
        VoxelShape shape = state.getShape(region, cursor);
        AABB box = shape.isEmpty() ? FULL_BLOCK : shape.bounds();
        int at = index * RtxSectionUpdate.LIGHT_INTS;
        lights[at + MIN_X] = Float.floatToIntBits((float) (x + box.minX));
        lights[at + MIN_Y] = Float.floatToIntBits((float) (y + box.minY));
        lights[at + MIN_Z] = Float.floatToIntBits((float) (z + box.minZ));
        lights[at + COLOR] = LightColors.packed(state);
        lights[at + MAX_X] = Float.floatToIntBits((float) (x + box.maxX));
        lights[at + MAX_Y] = Float.floatToIntBits((float) (y + box.maxY));
        lights[at + MAX_Z] = Float.floatToIntBits((float) (z + box.maxZ));
        lights[at + LEVEL] = levels(level + RANGE_MARGIN, (float) Math.pow(level / MAX_LEVEL, LIGHT_GAMMA));
    }

    private static int[] thinned(int[] lights, int count) {
        int[] kept = new int[RtxSectionUpdate.LIGHT_INTS * MAX_LIGHTS];
        float weight = (float) count / MAX_LIGHTS;
        for (int slot = 0; slot < MAX_LIGHTS; slot++) {
            int source = (int) ((long) slot * count / MAX_LIGHTS) * RtxSectionUpdate.LIGHT_INTS;
            int target = slot * RtxSectionUpdate.LIGHT_INTS;
            System.arraycopy(lights, source, kept, target, RtxSectionUpdate.LIGHT_INTS);
            float range = Float.float16ToFloat((short) (lights[source + LEVEL] & HALF_MASK));
            float strength = Float.float16ToFloat((short) (lights[source + LEVEL] >>> HIGH_SHIFT));
            kept[target + LEVEL] = levels(range, strength * weight);
        }
        return kept;
    }

    private static int levels(float range, float strength) {
        return (Float.floatToFloat16(range) & HALF_MASK) | (Float.floatToFloat16(strength) & HALF_MASK) << HIGH_SHIFT;
    }
}
