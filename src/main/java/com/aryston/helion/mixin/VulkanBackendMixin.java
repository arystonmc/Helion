package com.aryston.helion.mixin;

import com.aryston.helion.integration.RayTracingStartup;
import com.aryston.helion.render.rtx.RayTracing;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(VulkanBackend.class)
abstract class VulkanBackendMixin {
    @ModifyArg(
        method = "createVma",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/vma/Vma;vmaCreateAllocator(Lorg/lwjgl/util/vma/VmaAllocatorCreateInfo;Lorg/lwjgl/PointerBuffer;)I")
    )
    private static VmaAllocatorCreateInfo helion$allowDeviceAddresses(VmaAllocatorCreateInfo info, @Local(argsOnly = true) VkDevice device) {
        RayTracingStartup.ensureRequested();
        if (RayTracing.deviceRequested() && device.getCapabilities().VK_KHR_acceleration_structure) {
            info.flags(info.flags() | Vma.VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT);
        }
        return info;
    }
}
