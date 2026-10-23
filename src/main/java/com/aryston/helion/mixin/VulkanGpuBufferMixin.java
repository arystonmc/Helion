package com.aryston.helion.mixin;

import com.aryston.helion.render.rtx.RayTracing;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuBuffer;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(VulkanGpuBuffer.Direct.class)
abstract class VulkanGpuBufferMixin {
    private static final int GEOMETRY_USAGE = VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
    private static final int RAY_TRACING_INPUT = VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT
        | KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;

    @ModifyArg(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkBufferCreateInfo;usage(I)Lorg/lwjgl/vulkan/VkBufferCreateInfo;")
    )
    private static int helion$allowRayTracingInput(int usage, @Local(argsOnly = true) VulkanDevice device) {
        if ((usage & GEOMETRY_USAGE) != 0 && RayTracing.requested() && device.vkDevice().getCapabilities().VK_KHR_acceleration_structure) {
            return usage | RAY_TRACING_INPUT;
        }
        return usage;
    }
}
