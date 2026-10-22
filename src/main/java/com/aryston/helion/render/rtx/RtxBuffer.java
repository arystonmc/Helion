package com.aryston.helion.render.rtx;

import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;

record RtxBuffer(long vma, long buffer, long allocation, long size, long address, long mapped) {
    private static final int HOST_FLAGS = VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
    private static final int DEVICE_FLAGS = VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
    private static final int NO_FLAGS = 0;

    static RtxBuffer create(RtxDevice device, long size, int usage, boolean hostVisible) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo info = VkBufferCreateInfo.calloc(stack)
                .sType$Default()
                .size(size)
                .usage(usage | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT)
                .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            VmaAllocationCreateInfo allocation = VmaAllocationCreateInfo.calloc(stack)
                .flags(hostVisible ? Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT : NO_FLAGS)
                .requiredFlags(hostVisible ? HOST_FLAGS : DEVICE_FLAGS);
            LongBuffer buffer = stack.mallocLong(1);
            PointerBuffer allocated = stack.mallocPointer(1);
            VmaAllocationInfo result = VmaAllocationInfo.calloc(stack);
            check(Vma.vmaCreateBuffer(device.vma(), info, allocation, buffer, allocated, result), "buffer");
            long address = VK12.vkGetBufferDeviceAddress(device.vkDevice(), VkBufferDeviceAddressInfo.calloc(stack).sType$Default().buffer(buffer.get(0)));
            return new RtxBuffer(device.vma(), buffer.get(0), allocated.get(0), size, address, result.pMappedData());
        }
    }

    void destroy() {
        Vma.vmaDestroyBuffer(vma, buffer, allocation);
    }

    static void check(int result, String what) {
        if (result != VK10.VK_SUCCESS) {
            throw new IllegalStateException("Could not create the ray tracing " + what + " (VkResult " + result + ")");
        }
    }
}
