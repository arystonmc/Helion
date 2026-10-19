package com.aryston.helion.integration.vanilla;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class DynamicLightEmitters {
    static final int NO_LIGHT = 0;
    private static final int FIRE_LIGHT = 15;
    private static final int LAVA_LIGHT = 15;

    private DynamicLightEmitters() {
    }

    static int luminance(Entity entity) {
        int luminance = entity.isOnFire() ? FIRE_LIGHT : NO_LIGHT;
        if (entity instanceof ItemEntity item) {
            return Math.max(luminance, luminance(item.getItem()));
        }
        if (entity instanceof LivingEntity living) {
            return Math.max(luminance, Math.max(luminance(living.getMainHandItem()), luminance(living.getOffhandItem())));
        }
        return luminance;
    }

    private static int luminance(ItemStack stack) {
        if (stack.isEmpty()) {
            return NO_LIGHT;
        }
        if (stack.is(Items.LAVA_BUCKET)) {
            return LAVA_LIGHT;
        }
        if (stack.getItem() instanceof BlockItem block) {
            return block.getBlock().defaultBlockState().getLightEmission();
        }
        return NO_LIGHT;
    }
}
