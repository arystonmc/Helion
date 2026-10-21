package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.HelionClouds;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
abstract class CloudRendererMixin {
    @Inject(
        method = {
            "render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
            "renderOit(Lnet/minecraft/client/CloudStatus;Lnet/minecraft/client/renderer/oit/OitStage;"
                + "Lcom/mojang/renderpearl/api/textures/GpuTextureView;"
                + "Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;)V"
        },
        at = @At("HEAD"),
        cancellable = true
    )
    private void helion$skipUnderVolumetricClouds(CallbackInfo callback) {
        if (HelionClouds.isReplacingVanilla()) {
            callback.cancel();
        }
    }
}
