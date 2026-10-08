package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.HelionSky;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Inject(method = {"renderSkyDisc", "renderSunriseAndSunset"}, at = @At("HEAD"), cancellable = true)
    private void helion$skipUnderPhysicalSky(CallbackInfo callback) {
        if (HelionSky.isDrawingCelestialsOnly()) {
            callback.cancel();
        }
    }

    @ModifyArg(
        method = "renderMoon",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"
        ),
        index = 1
    )
    private Vector4f helion$dimMoon(Vector4f color) {
        return HelionSky.moonColor(color);
    }
}
