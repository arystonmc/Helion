package com.aryston.helion.integration.vanilla;

import java.util.SplittableRandom;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class AuroraNights {
    private static final long TICKS_PER_DAY = 24000L;
    private static final float AURORA_NIGHT_CHANCE = 0.25F;
    private static final float VISIBLE = 1.0F;
    private static final float HIDDEN = 0.0F;

    private AuroraNights() {
    }

    static float strength(ClientLevel level, Vec3 camera) {
        BlockPos position = BlockPos.containing(camera);
        if (!level.getBiome(position).value().coldEnoughToSnow(position, level.getSeaLevel())) {
            return HIDDEN;
        }
        long day = Math.floorDiv(level.getOverworldClockTime(), TICKS_PER_DAY);
        return new SplittableRandom(day).nextFloat() < AURORA_NIGHT_CHANCE ? VISIBLE : HIDDEN;
    }
}
