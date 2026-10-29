package com.aryston.helion.mixin;

import com.aryston.helion.render.HelionRenderCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "extractShadow", at = @At("TAIL"))
    private void helion$dropBlobShadowUnderPathTracedLight(EntityRenderState state, Minecraft minecraft, Level level, CallbackInfo callback) {
        if (HelionRenderCore.get().tracesLight()) {
            state.shadowPieces.clear();
        }
    }
}
