package com.aryston.helion.integration.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;

final class AmbientLightTracker {
    private static final float MAX_LIGHT_LEVEL = 15.0F;
    private static final double ADAPTATION_SECONDS = 1.5;
    private static final double NANOS_PER_SECOND = 1.0e9;

    private float smoothed;
    private long lastNanos;
    private boolean started;

    float update(BlockPos cameraPosition) {
        float target = skyLightAt(cameraPosition);
        long now = Util.getNanos();
        if (started) {
            double seconds = (now - lastNanos) / NANOS_PER_SECOND;
            float blend = (float) (1.0 - Math.exp(-seconds / ADAPTATION_SECONDS));
            smoothed += (target - smoothed) * blend;
        } else {
            smoothed = target;
            started = true;
        }
        lastNanos = now;
        return smoothed;
    }

    private static float skyLightAt(BlockPos position) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0.0F : level.getEffectiveSkyBrightness(position) / MAX_LIGHT_LEVEL;
    }
}
