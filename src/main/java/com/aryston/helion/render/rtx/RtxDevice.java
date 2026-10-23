package com.aryston.helion.render.rtx;

import com.aryston.helion.mixin.FrontendGpuDeviceAccessor;
import com.aryston.helion.mixin.VulkanCommandEncoderAccessor;
import com.aryston.helion.mixin.VulkanDeviceAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import java.util.Optional;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceAccelerationStructurePropertiesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;

final class RtxDevice {
    private final VulkanDevice device;
    private final long scratchAlignment;
    private final long storageOffsetAlignment;
    private final boolean entityTextures;

    private RtxDevice(VulkanDevice device, long scratchAlignment, long storageOffsetAlignment, boolean entityTextures) {
        this.device = device;
        this.scratchAlignment = scratchAlignment;
        this.storageOffsetAlignment = storageOffsetAlignment;
        this.entityTextures = entityTextures;
    }

    static Optional<RtxDevice> find() {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (!(device instanceof FrontendGpuDeviceAccessor frontend) || !(frontend.helion$backend() instanceof VulkanDevice vulkan)) {
            return Optional.empty();
        }
        FeatureSet enabled = ((VulkanDeviceAccessor) vulkan).helion$enabledFeatures();
        if (!enabled.contains(RtxFeature.FEATURE_SET)) {
            return Optional.empty();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDevice vkDevice = vulkan.vkDevice();
            VkPhysicalDeviceAccelerationStructurePropertiesKHR acceleration = VkPhysicalDeviceAccelerationStructurePropertiesKHR.calloc(stack).sType$Default();
            VkPhysicalDeviceProperties2 properties = VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(acceleration);
            VK11.vkGetPhysicalDeviceProperties2(vkDevice.getPhysicalDevice(), properties);
            long storageAlignment = properties.properties().limits().minStorageBufferOffsetAlignment();
            return Optional.of(new RtxDevice(
                vulkan, acceleration.minAccelerationStructureScratchOffsetAlignment(), storageAlignment, enabled.contains(RtxFeature.ENTITY_TEXTURES)
            ));
        }
    }

    VkDevice vkDevice() {
        return device.vkDevice();
    }

    long vma() {
        return device.vma();
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

    boolean entityTextures() {
        return entityTextures;
    }
}
