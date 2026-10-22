package com.aryston.helion.render.rtx;

import com.aryston.helion.mixin.FrontendGpuDeviceAccessor;
import com.aryston.helion.mixin.VulkanCommandEncoderAccessor;
import com.aryston.helion.mixin.VulkanDeviceAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import java.util.Optional;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceAccelerationStructurePropertiesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;

final class RtxDevice {
    private static final int NO_MEMORY_TYPE = -1;

    private final VulkanDevice device;
    private final int[] memoryTypeFlags;
    private final long scratchAlignment;
    private final long storageOffsetAlignment;

    private RtxDevice(VulkanDevice device, int[] memoryTypeFlags, long scratchAlignment, long storageOffsetAlignment) {
        this.device = device;
        this.memoryTypeFlags = memoryTypeFlags;
        this.scratchAlignment = scratchAlignment;
        this.storageOffsetAlignment = storageOffsetAlignment;
    }

    static Optional<RtxDevice> find() {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (!(device instanceof FrontendGpuDeviceAccessor frontend) || !(frontend.helion$backend() instanceof VulkanDevice vulkan)) {
            return Optional.empty();
        }
        if (!((VulkanDeviceAccessor) vulkan).helion$enabledFeatures().contains(RtxFeature.FEATURE_SET)) {
            return Optional.empty();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDevice vkDevice = vulkan.vkDevice();
            VkPhysicalDeviceMemoryProperties memory = VkPhysicalDeviceMemoryProperties.calloc(stack);
            VK11.vkGetPhysicalDeviceMemoryProperties(vkDevice.getPhysicalDevice(), memory);
            int[] flags = new int[memory.memoryTypeCount()];
            for (int index = 0; index < flags.length; index++) {
                flags[index] = memory.memoryTypes(index).propertyFlags();
            }
            VkPhysicalDeviceAccelerationStructurePropertiesKHR acceleration = VkPhysicalDeviceAccelerationStructurePropertiesKHR.calloc(stack).sType$Default();
            VkPhysicalDeviceProperties2 properties = VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(acceleration);
            VK11.vkGetPhysicalDeviceProperties2(vkDevice.getPhysicalDevice(), properties);
            long storageAlignment = properties.properties().limits().minStorageBufferOffsetAlignment();
            return Optional.of(new RtxDevice(vulkan, flags, acceleration.minAccelerationStructureScratchOffsetAlignment(), storageAlignment));
        }
    }

    VkDevice vkDevice() {
        return device.vkDevice();
    }

    VkCommandBuffer commandBuffer() {
        return ((VulkanCommandEncoderAccessor) device.createCommandEncoder()).helion$commandBuffer();
    }

    void memoryBarrier() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanCommandEncoder.memoryBarrier(commandBuffer(), stack);
        }
    }

    long scratchAlignment() {
        return scratchAlignment;
    }

    long storageOffsetAlignment() {
        return storageOffsetAlignment;
    }

    int memoryType(int allowedTypes, int requiredFlags) {
        for (int index = 0; index < memoryTypeFlags.length; index++) {
            if ((allowedTypes & 1 << index) != 0 && (memoryTypeFlags[index] & requiredFlags) == requiredFlags) {
                return index;
            }
        }
        return NO_MEMORY_TYPE;
    }

    static boolean found(int memoryType) {
        return memoryType != NO_MEMORY_TYPE;
    }
}
