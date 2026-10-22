package com.aryston.helion.mixin;

import com.aryston.helion.render.rtx.RtxSpike;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import org.lwjgl.vulkan.VK10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VulkanConst.class)
abstract class VulkanConstMixin {
    @Inject(method = "textureUsageToVk", at = @At("RETURN"), cancellable = true)
    private static void helion$addStorageUsage(int usage, GpuFormat format, CallbackInfoReturnable<Integer> callback) {
        if ((usage & RtxSpike.TEXTURE_USAGE_STORAGE) != 0) {
            callback.setReturnValue(callback.getReturnValue() | VK10.VK_IMAGE_USAGE_STORAGE_BIT);
        }
    }
}
