package com.aryston.helion.render.rtx;

import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAccelerationStructureInstanceKHR;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkTransformMatrixKHR;

final class RtxScene {
    private static final int INPUT_USAGE = KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;
    private static final int MIN_BOXES = 1;
    private static final int SINGLE_INSTANCE = 1;
    private static final int ALL_INSTANCES = 0xFF;
    private static final int NO_FLAGS = 0;
    private static final int[] TRANSFORM_DIAGONAL = {0, 5, 10};
    private static final float ONE = 1.0F;

    private final RtxArea area;
    private final int boxCount;
    private final RtxBuffer boxes;
    private final RtxBuffer instance;
    private final RtxAccelerationStructure bottom;
    private final RtxAccelerationStructure top;
    private boolean built;

    private RtxScene(RtxArea area, int boxCount, RtxBuffer boxes, RtxBuffer instance, RtxAccelerationStructure bottom, RtxAccelerationStructure top) {
        this.area = area;
        this.boxCount = boxCount;
        this.boxes = boxes;
        this.instance = instance;
        this.bottom = bottom;
        this.top = top;
    }

    static RtxScene create(RtxDevice device, RtxArea area, int[] exposed, int count) {
        long boxBytes = (long) Math.max(count, MIN_BOXES) * RtxAccelerationStructure.BOX_STRIDE;
        RtxBuffer boxes = RtxBuffer.create(device, boxBytes, INPUT_USAGE | VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true);
        writeBoxes(boxes, exposed, count);
        RtxAccelerationStructure bottom = RtxAccelerationStructure.create(device, RtxGeometry.boxes(boxes.address(), count));
        RtxBuffer instance = RtxBuffer.create(device, VkAccelerationStructureInstanceKHR.SIZEOF, INPUT_USAGE, true);
        writeInstance(instance, bottom.address());
        RtxAccelerationStructure top = RtxAccelerationStructure.create(device, RtxGeometry.instances(instance.address(), SINGLE_INSTANCE));
        return new RtxScene(area, count, boxes, instance, bottom, top);
    }

    boolean built() {
        return built;
    }

    void recordBottom(VkCommandBuffer commandBuffer) {
        bottom.record(commandBuffer);
    }

    void recordTop(VkCommandBuffer commandBuffer) {
        top.record(commandBuffer);
        built = true;
    }

    RtxArea area() {
        return area;
    }

    int boxCount() {
        return boxCount;
    }

    long topLevel() {
        return top.handle();
    }

    long boxBuffer() {
        return boxes.buffer();
    }

    long boxBytes() {
        return boxes.size();
    }

    long bytes() {
        return boxes.size() + instance.size() + bottom.bytes() + top.bytes();
    }

    void destroy(VkDevice vkDevice) {
        top.destroy(vkDevice);
        bottom.destroy(vkDevice);
        instance.destroy();
        boxes.destroy();
    }

    private static void writeBoxes(RtxBuffer boxes, int[] exposed, int count) {
        FloatBuffer floats = MemoryUtil.memFloatBuffer(boxes.mapped(), count * RtxAccelerationStructure.BOX_FLOATS);
        for (int index = 0; index < count; index++) {
            int block = exposed[index];
            float x = RtxBlocks.areaX(block);
            float y = RtxBlocks.areaY(block);
            float z = RtxBlocks.areaZ(block);
            floats.put(x).put(y).put(z).put(x + ONE).put(y + ONE).put(z + ONE);
        }
    }

    private static void writeInstance(RtxBuffer buffer, long bottomAddress) {
        MemoryUtil.memSet(buffer.mapped(), 0, VkAccelerationStructureInstanceKHR.SIZEOF);
        VkAccelerationStructureInstanceKHR instance = VkAccelerationStructureInstanceKHR.create(buffer.mapped());
        VkTransformMatrixKHR transform = instance.transform();
        for (int index : TRANSFORM_DIAGONAL) {
            transform.matrix(index, ONE);
        }
        instance.instanceCustomIndex(0)
            .mask(ALL_INSTANCES)
            .flags(NO_FLAGS)
            .accelerationStructureReference(bottomAddress);
    }
}
