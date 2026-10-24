package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.water.Footprint;
import com.aryston.helion.render.water.FootprintSource;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class VanillaFootprintSource implements FootprintSource {
    private static final double REACH = 32.0;
    private static final double STRIDE = 0.6;
    private static final double WET_FEET_SECONDS = 20.0;
    private static final double FORGET_SECONDS = 60.0;
    private static final double NEVER = Double.NEGATIVE_INFINITY;

    private final Int2ObjectOpenHashMap<Walker> walkers = new Int2ObjectOpenHashMap<>();

    @Override
    public List<Footprint> steps(double cameraX, double cameraZ, double seconds) {
        List<Footprint> steps = new ArrayList<>();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            walkers.clear();
            return steps;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || Math.abs(entity.getX() - cameraX) > REACH || Math.abs(entity.getZ() - cameraZ) > REACH) {
                continue;
            }
            Walker walker = walkers.computeIfAbsent(entity.getId(), id -> new Walker(entity.getX(), entity.getZ()));
            walker.seen = seconds;
            if (entity.isInWater()) {
                walker.wetUntil = seconds + WET_FEET_SECONDS;
                walker.moveTo(entity.getX(), entity.getZ());
                continue;
            }
            boolean wetFeet = seconds < walker.wetUntil;
            if (!entity.onGround() || !wetFeet || Math.hypot(entity.getX() - walker.x, entity.getZ() - walker.z) < STRIDE) {
                continue;
            }
            walker.moveTo(entity.getX(), entity.getZ());
            walker.left = !walker.left;
            steps.add(new Footprint(entity.getX(), entity.getZ(), entity.getYRot(), walker.left, seconds));
        }
        walkers.values().removeIf(walker -> seconds - walker.seen > FORGET_SECONDS || seconds < walker.seen);
        return steps;
    }

    private static final class Walker {
        private double x;
        private double z;
        private double wetUntil = NEVER;
        private double seen;
        private boolean left;

        private Walker(double x, double z) {
            this.x = x;
            this.z = z;
        }

        private void moveTo(double nextX, double nextZ) {
            x = nextX;
            z = nextZ;
        }
    }
}
