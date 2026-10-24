package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.HelionUnderwaterFog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WaterFogEnvironment.class)
abstract class WaterFogEnvironmentMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void helion$extendUnderRealisticWater(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo callback) {
        fog.environmentalEnd = HelionUnderwaterFog.extendedEnd(fog.environmentalEnd);
        fog.skyEnd = fog.environmentalEnd;
        fog.cloudEnd = fog.environmentalEnd;
    }
}
