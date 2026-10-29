package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.VanillaLightSource;
import com.aryston.helion.render.HelionRenderCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "extractShadow", at = @At("TAIL"))
    private void helion$dropBlobShadowUnderPathTracedLight(EntityRenderState state, Minecraft minecraft, Level level, CallbackInfo callback) {
        if (HelionRenderCore.get().tracesLight()) {
            state.shadowPieces.clear();
        }
    }

    @Inject(method = "getBlockLightLevel", at = @At("RETURN"), cancellable = true)
    private void helion$lightDroppedEmitters(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        if (HelionRenderCore.get().tracesLight()) {
            callback.setReturnValue(Math.max(callback.getReturnValueI(), VanillaLightSource.itemLight(entity)));
        }
    }
}
