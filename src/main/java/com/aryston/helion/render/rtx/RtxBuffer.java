package com.aryston.helion.render.rtx;

import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateFlagsInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

record RtxBuffer(long buffer, long memory, long size, long address, long mapped) {
    private static final int HOST_FLAGS = VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
    private static final int DEVICE_FLAGS = VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
    private static final long NOT_MAPPED = 0L;
    private static final int NO_FLAGS = 0;
    private static final long START = 0L;

    static RtxBuffer create(RtxDevice device, long size, int usage, boolean hostVisible) {
        VkDevice vkDevice = device.vkDevice();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo info = VkBufferCreateInfo.calloc(stack)
                .sType$Default()
                .size(size)
                .usage(usage | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT)
                .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            check(VK10.vkCreateBuffer(vkDevice, info, null, handle), "buffer");
            long buffer = handle.get(0);
            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            VK10.vkGetBufferMemoryRequirements(vkDevice, buffer, requirements);
            int memoryType = device.memoryType(requirements.memoryTypeBits(), hostVisible ? HOST_FLAGS : DEVICE_FLAGS);
            if (!RtxDevice.found(memoryType)) {
                VK10.vkDestroyBuffer(vkDevice, buffer, null);
                throw new IllegalStateException("No memory type for an RTX spike buffer");
            }
            VkMemoryAllocateFlagsInfo flags = VkMemoryAllocateFlagsInfo.calloc(stack)
                .sType$Default()
                .flags(VK12.VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT);
            VkMemoryAllocateInfo allocation = VkMemoryAllocateInfo.calloc(stack)
                .sType$Default()
                .pNext(flags)
                .allocationSize(requirements.size())
                .memoryTypeIndex(memoryType);
            LongBuffer memoryHandle = stack.mallocLong(1);
            check(VK10.vkAllocateMemory(vkDevice, allocation, null, memoryHandle), "memory");
            long memory = memoryHandle.get(0);
            check(VK10.vkBindBufferMemory(vkDevice, buffer, memory, START), "memory binding");
            long address = VK12.vkGetBufferDeviceAddress(vkDevice, VkBufferDeviceAddressInfo.calloc(stack).sType$Default().buffer(buffer));
            long mapped = NOT_MAPPED;
            if (hostVisible) {
                PointerBuffer pointer = stack.mallocPointer(1);
                check(VK10.vkMapMemory(vkDevice, memory, START, size, NO_FLAGS, pointer), "memory mapping");
                mapped = pointer.get(0);
            }
            return new RtxBuffer(buffer, memory, size, address, mapped);
        }
    }

    void destroy(VkDevice vkDevice) {
        VK10.vkDestroyBuffer(vkDevice, buffer, null);
        VK10.vkFreeMemory(vkDevice, memory, null);
    }

    static void check(int result, String what) {
        if (result != VK10.VK_SUCCESS) {
            throw new IllegalStateException("Could not create the RTX spike " + what + " (VkResult " + result + ")");
        }
    }
}
