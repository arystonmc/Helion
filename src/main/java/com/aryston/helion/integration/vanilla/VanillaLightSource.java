package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.rtx.RtxDynamicLight;
import com.aryston.helion.render.rtx.RtxLightSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class VanillaLightSource implements RtxLightSource {
    private static final double REACH = 48.0;
    private static final double REACH_SQUARED = REACH * REACH;
    private static final double HELD_HEIGHT = 0.45;
    private static final double HELD_SIDE = 0.6;
    private static final double HELD_FORWARD = 0.25;
    private static final double HAND_REACH = 0.35;
    private static final double HAND_DROP = 0.3;
    private static final double HELD_RADIUS = 0.1;
    private static final double ITEM_RADIUS = 0.25;
    private static final double ITEM_LIFT = 0.2;
    private static final int NO_LIGHT = 0;
    private static final float FULL_AMBIENT = 1.0F;
    private static final float NO_AMBIENT = 0.0F;

    @Override
    public List<RtxDynamicLight> lights(double cameraX, double cameraY, double cameraZ) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return List.of();
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Entity viewer = minecraft.options.getCameraType().isFirstPerson() ? minecraft.getCameraEntity() : null;
        Vec3 camera = new Vec3(cameraX, cameraY, cameraZ);
        List<Found> found = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            Vec3 position = entity.getPosition(partialTick);
            double distance = position.distanceToSqr(camera);
            if (distance > REACH_SQUARED) {
                continue;
            }
            RtxDynamicLight light = light(entity, position, entity == viewer, partialTick);
            if (light != null) {
                found.add(new Found(distance, light));
            }
        }
        found.sort(Comparator.comparingDouble(Found::distance));
        return found.stream().limit(MAX_LIGHTS).map(Found::light).toList();
    }

    @Override
    public float ambientShare() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !level.dimensionType().hasSkyLight()) {
            return FULL_AMBIENT;
        }
        return minecraft.getCameraEntity() instanceof LivingEntity living && living.hasEffect(MobEffects.NIGHT_VISION) ? FULL_AMBIENT : NO_AMBIENT;
    }

    public static int itemLight(Entity entity) {
        BlockState emitter = entity instanceof ItemEntity item ? emitter(item.getItem()) : null;
        return emitter == null ? NO_LIGHT : level(emitter);
    }

    private static @Nullable RtxDynamicLight light(Entity entity, Vec3 position, boolean viewed, float partialTick) {
        AABB box = entity.getBoundingBox().move(position.subtract(entity.position()));
        if (entity.displayFireAnimation()) {
            return light(box, Blocks.FIRE.defaultBlockState());
        }
        if (entity instanceof ItemEntity item) {
            BlockState emitter = emitter(item.getItem());
            return emitter == null ? null : light(around(box.getCenter().add(0.0, ITEM_LIFT, 0.0), ITEM_RADIUS), emitter);
        }
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        BlockState main = emitter(living.getMainHandItem());
        BlockState off = emitter(living.getOffhandItem());
        boolean mainHand = main != null && (off == null || level(main) >= level(off));
        BlockState emitter = mainHand ? main : off;
        if (emitter == null) {
            return null;
        }
        Vec3 hand = viewed
            ? entity.getEyePosition(partialTick).add(entity.getViewVector(partialTick).scale(HAND_REACH)).subtract(0.0, HAND_DROP, 0.0)
            : held(living, position, mainHand == (living.getMainArm() == HumanoidArm.RIGHT), partialTick);
        return light(around(hand, HELD_RADIUS), emitter);
    }

    private static Vec3 held(LivingEntity living, Vec3 position, boolean right, float partialTick) {
        double yaw = Math.toRadians(Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot));
        double side = (right ? 1.0 : -1.0) * living.getBbWidth() * HELD_SIDE;
        return position.add(
            -Math.cos(yaw) * side - Math.sin(yaw) * HELD_FORWARD,
            living.getBbHeight() * HELD_HEIGHT,
            -Math.sin(yaw) * side + Math.cos(yaw) * HELD_FORWARD
        );
    }

    private static RtxDynamicLight light(AABB box, BlockState emitter) {
        return new RtxDynamicLight(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, LightColors.packed(emitter), RtxSectionLights.levels(emitter, level(emitter)));
    }

    private static AABB around(Vec3 center, double radius) {
        return new AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
    }

    private static @Nullable BlockState emitter(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(Items.LAVA_BUCKET)) {
            return Blocks.LAVA.defaultBlockState();
        }
        if (stack.getItem() instanceof BlockItem block) {
            BlockState state = block.getBlock().defaultBlockState();
            return level(state) > NO_LIGHT ? state : null;
        }
        return null;
    }

    private static int level(BlockState state) {
        return state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private record Found(double distance, RtxDynamicLight light) {
    }
}
