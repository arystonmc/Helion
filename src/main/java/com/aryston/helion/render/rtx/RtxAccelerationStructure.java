package com.aryston.helion.render.rtx;

import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAccelerationStructureBuildGeometryInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureBuildRangeInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureBuildSizesInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureCreateInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureDeviceAddressInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureGeometryKHR;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;

final class RtxAccelerationStructure {
    static final int BOX_FLOATS = 6;
    static final int BOX_STRIDE = BOX_FLOATS * Float.BYTES;

    private static final int SINGLE = 1;

    private final boolean topLevel;
    private final long inputAddress;
    private final int count;
    private final long handle;
    private final long address;
    private final RtxBuffer storage;
    private final RtxBuffer scratch;
    private final long scratchAddress;

    private RtxAccelerationStructure(
        boolean topLevel, long inputAddress, int count, long handle, long address, RtxBuffer storage, RtxBuffer scratch, long scratchAddress
    ) {
        this.topLevel = topLevel;
        this.inputAddress = inputAddress;
        this.count = count;
        this.handle = handle;
        this.address = address;
        this.storage = storage;
        this.scratch = scratch;
        this.scratchAddress = scratchAddress;
    }

    static RtxAccelerationStructure create(RtxDevice device, boolean topLevel, long inputAddress, int count) {
        VkDevice vkDevice = device.vkDevice();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAccelerationStructureBuildGeometryInfoKHR.Buffer info = buildInfo(stack, topLevel, inputAddress);
            VkAccelerationStructureBuildSizesInfoKHR sizes = VkAccelerationStructureBuildSizesInfoKHR.calloc(stack).sType$Default();
            KHRAccelerationStructure.vkGetAccelerationStructureBuildSizesKHR(
                vkDevice, KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR, info.get(0), stack.ints(count), sizes
            );
            RtxBuffer storage = RtxBuffer.create(
                device, sizes.accelerationStructureSize(), KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR, false
            );
            VkAccelerationStructureCreateInfoKHR createInfo = VkAccelerationStructureCreateInfoKHR.calloc(stack)
                .sType$Default()
                .buffer(storage.buffer())
                .size(sizes.accelerationStructureSize())
                .type(type(topLevel));
            LongBuffer handle = stack.mallocLong(1);
            RtxBuffer.check(KHRAccelerationStructure.vkCreateAccelerationStructureKHR(vkDevice, createInfo, null, handle), "acceleration structure");
            long alignment = device.scratchAlignment();
            RtxBuffer scratch = RtxBuffer.create(device, sizes.buildScratchSize() + alignment, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, false);
            long scratchAddress = (scratch.address() + alignment - 1) / alignment * alignment;
            long address = KHRAccelerationStructure.vkGetAccelerationStructureDeviceAddressKHR(
                vkDevice, VkAccelerationStructureDeviceAddressInfoKHR.calloc(stack).sType$Default().accelerationStructure(handle.get(0))
            );
            return new RtxAccelerationStructure(topLevel, inputAddress, count, handle.get(0), address, storage, scratch, scratchAddress);
        }
    }

    void record(VkCommandBuffer commandBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAccelerationStructureBuildGeometryInfoKHR.Buffer info = buildInfo(stack, topLevel, inputAddress).dstAccelerationStructure(handle);
            info.scratchData().deviceAddress(scratchAddress);
            VkAccelerationStructureBuildRangeInfoKHR.Buffer range = VkAccelerationStructureBuildRangeInfoKHR.calloc(SINGLE, stack).primitiveCount(count);
            KHRAccelerationStructure.vkCmdBuildAccelerationStructuresKHR(commandBuffer, info, stack.pointers(range.address()));
        }
    }

    long handle() {
        return handle;
    }

    long address() {
        return address;
    }

    long bytes() {
        return storage.size() + scratch.size();
    }

    void destroy(VkDevice vkDevice) {
        KHRAccelerationStructure.vkDestroyAccelerationStructureKHR(vkDevice, handle, null);
        storage.destroy(vkDevice);
        scratch.destroy(vkDevice);
    }

    private static VkAccelerationStructureBuildGeometryInfoKHR.Buffer buildInfo(MemoryStack stack, boolean topLevel, long inputAddress) {
        VkAccelerationStructureGeometryKHR.Buffer geometry = VkAccelerationStructureGeometryKHR.calloc(SINGLE, stack)
            .sType$Default()
            .flags(KHRAccelerationStructure.VK_GEOMETRY_OPAQUE_BIT_KHR);
        if (topLevel) {
            geometry.geometryType(KHRAccelerationStructure.VK_GEOMETRY_TYPE_INSTANCES_KHR);
            geometry.geometry().instances().sType$Default().arrayOfPointers(false).data().deviceAddress(inputAddress);
        } else {
            geometry.geometryType(KHRAccelerationStructure.VK_GEOMETRY_TYPE_AABBS_KHR);
            geometry.geometry().aabbs().sType$Default().stride(BOX_STRIDE).data().deviceAddress(inputAddress);
        }
        return VkAccelerationStructureBuildGeometryInfoKHR.calloc(SINGLE, stack)
            .sType$Default()
            .type(type(topLevel))
            .flags(KHRAccelerationStructure.VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
            .mode(KHRAccelerationStructure.VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR)
            .geometryCount(SINGLE)
            .pGeometries(geometry);
    }

    private static int type(boolean topLevel) {
        return topLevel
            ? KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR
            : KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR;
    }
}
