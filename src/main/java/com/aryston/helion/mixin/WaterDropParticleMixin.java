package com.aryston.helion.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.WaterDropParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WaterDropParticle.class)
abstract class WaterDropParticleMixin {
    @Inject(method = "getLayer", at = @At("HEAD"), cancellable = true)
    private void helion$blendRainSplashes(CallbackInfoReturnable<SingleQuadParticle.Layer> callback) {
        callback.setReturnValue(SingleQuadParticle.Layer.TRANSLUCENT);
    }
}
