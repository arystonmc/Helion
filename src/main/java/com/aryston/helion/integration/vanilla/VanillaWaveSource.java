package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.water.WaveImpulse;
import com.aryston.helion.render.water.WaveSource;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

final class VanillaWaveSource implements WaveSource {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double FEET_INSET = 0.05;
    private static final double ABOVE_HEAD = 0.5;
    private static final float WAKE_PER_SPEED = 0.004F;
    private static final float MAX_WAKE = 0.03F;
    private static final double MIN_WAKE_SPEED = 0.2;
    private static final float SPLASH_PER_SPEED = 0.02F;
    private static final float MAX_SPLASH = 0.25F;
    private static final double MIN_SPLASH_SPEED = 2.0;
    private static final float WAKE_RADIUS_SHARE = 0.6F;
    private static final float SPLASH_RADIUS_SHARE = 1.2F;
    private static final float MIN_RADIUS = 0.3F;
    private static final boolean CONTINUOUS = false;
    private static final boolean SPLASH = true;
    private static final int ROOF_MARGIN = 8;
    private static final float STEP_RADIUS_SHARE = 0.4F;
    private static final float STEP_PER_SPEED = 0.003F;
    private static final float MAX_STEP = 0.02F;
    private static final int MAX_ROOF_THICKNESS = 3;
    private static final int MAX_HEADROOM = 12;

    private IntSet touching = new IntOpenHashSet();

    @Override
    public List<WaveImpulse> impulses(double cameraX, double cameraZ, double reach, boolean puddles) {
        ClientLevel level = Minecraft.getInstance().level;
        List<WaveImpulse> impulses = new ArrayList<>();
        IntSet nowTouching = new IntOpenHashSet();
        if (level == null) {
            touching = nowTouching;
            return impulses;
        }
        for (Entity entity : level.entitiesForRendering()) {
            double x = entity.getX();
            double z = entity.getZ();
            if (Math.abs(x - cameraX) > reach || Math.abs(z - cameraZ) > reach) {
                continue;
            }
            if (!touchesSurface(level, entity.getBoundingBox(), x, z)) {
                if (puddles && entity.onGround()) {
                    step(entity, x, z, impulses);
                }
                continue;
            }
            nowTouching.add(entity.getId());
            float width = entity.getBbWidth();
            double speed = Math.hypot(x - entity.xo, z - entity.zo) * TICKS_PER_SECOND;
            if (speed > MIN_WAKE_SPEED) {
                impulses.add(new WaveImpulse(x, z, Math.max(width * WAKE_RADIUS_SHARE, MIN_RADIUS), Math.min((float) speed * WAKE_PER_SPEED, MAX_WAKE), CONTINUOUS));
            }
            double falling = (entity.yo - entity.getY()) * TICKS_PER_SECOND;
            if (!touching.contains(entity.getId()) && falling > MIN_SPLASH_SPEED) {
                impulses.add(new WaveImpulse(x, z, Math.max(width * SPLASH_RADIUS_SHARE, MIN_RADIUS), Math.min((float) falling * SPLASH_PER_SPEED, MAX_SPLASH), SPLASH));
            }
        }
        touching = nowTouching;
        return impulses;
    }

    @Override
    public boolean holdsWaves(int blockX, int blockZ, int cameraBlockY) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return true;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ) - 1;
        if (top > cameraBlockY + ROOF_MARGIN) {
            return true;
        }
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(blockX, top, blockZ);
        int lowest = top - MAX_ROOF_THICKNESS;
        BlockState state = level.getBlockState(position);
        while (position.getY() > lowest && !state.isAir()) {
            if (state.getFluidState().is(FluidTags.WATER)) {
                return true;
            }
            state = level.getBlockState(position.move(Direction.DOWN));
        }
        int floor = position.getY() - MAX_HEADROOM;
        while (position.getY() > floor && state.isAir()) {
            state = level.getBlockState(position.move(Direction.DOWN));
        }
        return state.getFluidState().is(FluidTags.WATER);
    }

    @Override
    public float rainFloor(int blockX, int blockZ) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? Float.MAX_VALUE : level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
    }

    private static void step(Entity entity, double x, double z, List<WaveImpulse> impulses) {
        double speed = Math.hypot(x - entity.xo, z - entity.zo) * TICKS_PER_SECOND;
        if (speed > MIN_WAKE_SPEED) {
            impulses.add(new WaveImpulse(x, z, Math.max(entity.getBbWidth() * STEP_RADIUS_SHARE, MIN_RADIUS), Math.min((float) speed * STEP_PER_SPEED, MAX_STEP), CONTINUOUS));
        }
    }

    private static boolean touchesSurface(ClientLevel level, AABB box, double x, double z) {
        return isWater(level, x, box.minY + FEET_INSET, z) && !isWater(level, x, box.maxY + ABOVE_HEAD, z);
    }

    private static boolean isWater(ClientLevel level, double x, double y, double z) {
        return level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER);
    }
}
