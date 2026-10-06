package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.CelestialOnlySky;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Inject(method = {"renderSkyDisc", "renderSunriseAndSunset"}, at = @At("HEAD"), cancellable = true)
    private void helion$skipUnderPhysicalSky(CallbackInfo callback) {
        if (CelestialOnlySky.isDrawing()) {
            callback.cancel();
        }
    }
}
