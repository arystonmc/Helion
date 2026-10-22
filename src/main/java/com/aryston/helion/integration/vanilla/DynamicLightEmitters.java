package com.aryston.helion.integration.vanilla;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class DynamicLightEmitters {
    static final int NO_LIGHT = 0;
    static final Emission NONE = new Emission(NO_LIGHT, Blocks.AIR.defaultBlockState());
    private static final int FIRE_LIGHT = 15;
    private static final int LAVA_LIGHT = 15;

    private DynamicLightEmitters() {
    }

    static Emission emission(Entity entity) {
        Emission emission = entity.isOnFire() ? new Emission(FIRE_LIGHT, Blocks.FIRE.defaultBlockState()) : NONE;
        if (entity instanceof ItemEntity item) {
            return brighter(emission, emission(item.getItem()));
        }
        if (entity instanceof LivingEntity living) {
            return brighter(emission, brighter(emission(living.getMainHandItem()), emission(living.getOffhandItem())));
        }
        return emission;
    }

    private static Emission emission(ItemStack stack) {
        if (stack.isEmpty()) {
            return NONE;
        }
        if (stack.is(Items.LAVA_BUCKET)) {
            return new Emission(LAVA_LIGHT, Blocks.LAVA.defaultBlockState());
        }
        if (stack.getItem() instanceof BlockItem block) {
            BlockState state = block.getBlock().defaultBlockState();
            return new Emission(state.getLightEmission(), state);
        }
        return NONE;
    }

    private static Emission brighter(Emission first, Emission second) {
        return second.level() > first.level() ? second : first;
    }

    record Emission(int level, BlockState state) {
    }
}
