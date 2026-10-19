package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.DynamicLightTracker;
import com.aryston.helion.render.HelionRenderCore;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "extractShadow", at = @At("HEAD"), cancellable = true)
    private void helion$skipBlobShadowUnderSunShadows(EntityRenderState state, Minecraft minecraft, Level level, CallbackInfo callback) {
        HelionRenderCore core = HelionRenderCore.get();
        if (core.isActive() && core.shadows().drawnLastFrame()) {
            state.shadowPieces.clear();
            state.shadowRadius = 0.0F;
            callback.cancel();
        }
    }

    @ModifyReturnValue(method = "getPackedLightCoords", at = @At("RETURN"))
    private int helion$addDynamicLight(int lightCoords, Entity entity, float partialTickTime) {
        return DynamicLightTracker.brighten(entity, lightCoords);
    }
}
