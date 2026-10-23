package com.aryston.helion.render.rtx;

import java.nio.LongBuffer;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAccelerationStructureBuildGeometryInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureBuildRangeInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureBuildSizesInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureCreateInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureDeviceAddressInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureGeometryKHR;
import org.lwjgl.vulkan.VkAccelerationStructureGeometryTrianglesDataKHR;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;

final class RtxAccelerationStructure {
    static final int BOX_FLOATS = 6;
    static final int BOX_STRIDE = BOX_FLOATS * Float.BYTES;
    static final int VERTEX_FLOATS = 3;
    static final int VERTEX_STRIDE = VERTEX_FLOATS * Float.BYTES;

    private static final int SINGLE = 1;
    private static final int LAST_OFFSET = 1;
    private static final long NO_ADDRESS = 0L;
    private static final long NO_BYTES = 0L;
    private static final int NO_FLAGS = 0;

    private final List<RtxGeometry> geometries;
    private final long handle;
    private final long address;
    private final RtxBuffer storage;
    private final long scratchSize;
    private final @Nullable RtxBuffer scratch;
    private final long scratchAddress;

    private RtxAccelerationStructure(
        List<RtxGeometry> geometries, long handle, long address, RtxBuffer storage, long scratchSize, @Nullable RtxBuffer scratch, long scratchAddress
    ) {
        this.geometries = geometries;
        this.handle = handle;
        this.address = address;
        this.storage = storage;
        this.scratchSize = scratchSize;
        this.scratch = scratch;
        this.scratchAddress = scratchAddress;
    }

    static RtxAccelerationStructure create(RtxDevice device, RtxGeometry geometry) {
        return create(device, List.of(geometry), true);
    }

    static RtxAccelerationStructure createForSharedScratch(RtxDevice device, RtxGeometry geometry) {
        return createForSharedScratch(device, List.of(geometry));
    }

    static RtxAccelerationStructure createForSharedScratch(RtxDevice device, List<RtxGeometry> geometries) {
        return create(device, geometries, false);
    }

    private static RtxAccelerationStructure create(RtxDevice device, List<RtxGeometry> geometries, boolean ownScratch) {
        VkDevice vkDevice = device.vkDevice();
        RtxGeometry geometry = geometries.getFirst();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAccelerationStructureBuildGeometryInfoKHR.Buffer info = buildInfo(stack, geometries);
            VkAccelerationStructureBuildSizesInfoKHR sizes = VkAccelerationStructureBuildSizesInfoKHR.calloc(stack).sType$Default();
            int[] counts = geometries.stream().mapToInt(RtxGeometry::primitiveCount).toArray();
            KHRAccelerationStructure.vkGetAccelerationStructureBuildSizesKHR(
                vkDevice, KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR, info.get(0), stack.ints(counts), sizes
            );
            RtxBuffer storage = RtxBuffer.create(
                device, sizes.accelerationStructureSize(), KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR, false
            );
            VkAccelerationStructureCreateInfoKHR createInfo = VkAccelerationStructureCreateInfoKHR.calloc(stack)
                .sType$Default()
                .buffer(storage.buffer())
                .size(sizes.accelerationStructureSize())
                .type(type(geometry));
            LongBuffer handle = stack.mallocLong(1);
            RtxBuffer.check(KHRAccelerationStructure.vkCreateAccelerationStructureKHR(vkDevice, createInfo, null, handle), "acceleration structure");
            RtxBuffer scratch = ownScratch ? scratchBuffer(device, sizes.buildScratchSize()) : null;
            long scratchAddress = scratch == null ? NO_ADDRESS : aligned(scratch.address(), device.scratchAlignment());
            long address = KHRAccelerationStructure.vkGetAccelerationStructureDeviceAddressKHR(
                vkDevice, VkAccelerationStructureDeviceAddressInfoKHR.calloc(stack).sType$Default().accelerationStructure(handle.get(0))
            );
            return new RtxAccelerationStructure(geometries, handle.get(0), address, storage, sizes.buildScratchSize(), scratch, scratchAddress);
        }
    }

    void record(VkCommandBuffer commandBuffer) {
        record(commandBuffer, geometries, scratchAddress);
    }

    void record(VkCommandBuffer commandBuffer, RtxGeometry input) {
        record(commandBuffer, List.of(input), scratchAddress);
    }

    void recordWithScratch(VkCommandBuffer commandBuffer, long sharedScratchAddress) {
        record(commandBuffer, geometries, sharedScratchAddress);
    }

    static RtxBuffer scratchBuffer(RtxDevice device, long size) {
        return RtxBuffer.create(device, size + device.scratchAlignment(), VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, false);
    }

    static long aligned(long address, long alignment) {
        return (address + alignment - LAST_OFFSET) / alignment * alignment;
    }

    private void record(VkCommandBuffer commandBuffer, List<RtxGeometry> inputs, long buildScratchAddress) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAccelerationStructureBuildGeometryInfoKHR.Buffer info = buildInfo(stack, inputs).dstAccelerationStructure(handle);
            info.scratchData().deviceAddress(buildScratchAddress);
            VkAccelerationStructureBuildRangeInfoKHR.Buffer range = VkAccelerationStructureBuildRangeInfoKHR.calloc(inputs.size(), stack);
            for (int index = 0; index < inputs.size(); index++) {
                range.get(index).primitiveCount(inputs.get(index).primitiveCount());
            }
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
        return storage.size() + (scratch == null ? NO_BYTES : scratch.size());
    }

    long scratchSize() {
        return scratchSize;
    }

    void destroy(VkDevice vkDevice) {
        KHRAccelerationStructure.vkDestroyAccelerationStructureKHR(vkDevice, handle, null);
        storage.destroy();
        if (scratch != null) {
            scratch.destroy();
        }
    }

    private static VkAccelerationStructureBuildGeometryInfoKHR.Buffer buildInfo(MemoryStack stack, List<RtxGeometry> geometries) {
        VkAccelerationStructureGeometryKHR.Buffer described = VkAccelerationStructureGeometryKHR.calloc(geometries.size(), stack);
        for (int index = 0; index < geometries.size(); index++) {
            describe(described.get(index), geometries.get(index));
        }
        return VkAccelerationStructureBuildGeometryInfoKHR.calloc(SINGLE, stack)
            .sType$Default()
            .type(type(geometries.getFirst()))
            .flags(KHRAccelerationStructure.VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
            .mode(KHRAccelerationStructure.VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR)
            .geometryCount(geometries.size())
            .pGeometries(described);
    }

    private static void describe(VkAccelerationStructureGeometryKHR described, RtxGeometry geometry) {
        described.sType$Default().flags(geometry.opaque() ? KHRAccelerationStructure.VK_GEOMETRY_OPAQUE_BIT_KHR : NO_FLAGS);
        switch (geometry.kind()) {
            case INSTANCES -> {
                described.geometryType(KHRAccelerationStructure.VK_GEOMETRY_TYPE_INSTANCES_KHR);
                described.geometry().instances().sType$Default().arrayOfPointers(false).data().deviceAddress(geometry.dataAddress());
            }
            case BOXES -> {
                described.geometryType(KHRAccelerationStructure.VK_GEOMETRY_TYPE_AABBS_KHR);
                described.geometry().aabbs().sType$Default().stride(BOX_STRIDE).data().deviceAddress(geometry.dataAddress());
            }
            case TRIANGLES -> {
                described.geometryType(KHRAccelerationStructure.VK_GEOMETRY_TYPE_TRIANGLES_KHR);
                VkAccelerationStructureGeometryTrianglesDataKHR triangles = described.geometry().triangles()
                    .sType$Default()
                    .vertexFormat(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                    .vertexStride(VERTEX_STRIDE)
                    .maxVertex(Math.max(geometry.vertexCount() - LAST_OFFSET, 0))
                    .indexType(VK10.VK_INDEX_TYPE_UINT32);
                triangles.vertexData().deviceAddress(geometry.dataAddress());
                triangles.indexData().deviceAddress(geometry.indexAddress());
            }
        }
    }

    private static int type(RtxGeometry geometry) {
        return geometry.kind() == RtxGeometry.Kind.INSTANCES
            ? KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR
            : KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR;
    }
}
