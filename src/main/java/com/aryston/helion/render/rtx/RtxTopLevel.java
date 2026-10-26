package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import java.util.List;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAccelerationStructureInstanceKHR;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkTransformMatrixKHR;

final class RtxTopLevel {
    static final int CAPACITY = 1024;

    private static final String LABEL = "Helion Ray Tracing Top Level";
    private static final int FRAMES_IN_FLIGHT = 4;
    private static final int USAGE = KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;
    private static final int SURFACE_TABLE_USAGE = VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
    static final int SECTION_RAYS = 0x01;
    static final int ENTITY_RAYS = 0x02;
    static final int ENTITY_REFLECTION_RAYS = 0x04;
    static final int ENTITY_REFLECTION_INDEX = 0xFFFFFF;
    private static final int ENTITY_INSTANCES = 2;
    private static final int SURFACE_ENTRY_BYTES = 4 * Integer.BYTES;
    private static final int SOLID_QUADS_OFFSET = Long.BYTES;
    private static final int[] DIAGONAL = {0, 5, 10};
    private static final int TRANSLATE_X = 3;
    private static final int TRANSLATE_Y = 7;
    private static final int TRANSLATE_Z = 11;
    private static final float ONE = 1.0F;

    private final RtxBuffer[] instances;
    private final RtxBuffer[] surfaceTables;
    private final RtxAccelerationStructure structure;
    private final TrackedResource tracked;
    private int frame;
    private int count;

    private RtxTopLevel(RtxBuffer[] instances, RtxBuffer[] surfaceTables, RtxAccelerationStructure structure, TrackedResource tracked) {
        this.instances = instances;
        this.surfaceTables = surfaceTables;
        this.structure = structure;
        this.tracked = tracked;
    }

    static RtxTopLevel create(RtxDevice device, GpuResources resources) {
        RtxBuffer[] instances = new RtxBuffer[FRAMES_IN_FLIGHT];
        RtxBuffer[] surfaceTables = new RtxBuffer[FRAMES_IN_FLIGHT];
        long bytes = 0L;
        for (int slot = 0; slot < FRAMES_IN_FLIGHT; slot++) {
            instances[slot] = RtxBuffer.create(device, (long) CAPACITY * VkAccelerationStructureInstanceKHR.SIZEOF, USAGE, true);
            surfaceTables[slot] = RtxBuffer.create(device, (long) CAPACITY * SURFACE_ENTRY_BYTES, SURFACE_TABLE_USAGE, true);
            bytes += instances[slot].size() + surfaceTables[slot].size();
        }
        RtxAccelerationStructure structure = RtxAccelerationStructure.create(device, RtxGeometry.instances(instances[0].address(), CAPACITY));
        TrackedResource tracked = resources.track(LABEL, bytes + structure.bytes(), () -> {
            structure.destroy(device.vkDevice());
            for (int slot = 0; slot < FRAMES_IN_FLIGHT; slot++) {
                instances[slot].destroy();
                surfaceTables[slot].destroy();
            }
        });
        return new RtxTopLevel(instances, surfaceTables, structure, tracked);
    }

    void write(List<RtxSectionBlas> sections, RtxSectionPos anchor) {
        frame = (frame + 1) % FRAMES_IN_FLIGHT;
        long base = instances[frame].mapped();
        long table = surfaceTables[frame].mapped();
        count = Math.min(sections.size(), CAPACITY);
        MemoryUtil.memSet(base, 0, (long) count * VkAccelerationStructureInstanceKHR.SIZEOF);
        for (int index = 0; index < count; index++) {
            RtxSectionBlas section = sections.get(index);
            VkAccelerationStructureInstanceKHR instance = VkAccelerationStructureInstanceKHR.create(base + (long) index * VkAccelerationStructureInstanceKHR.SIZEOF);
            VkTransformMatrixKHR transform = instance.transform();
            for (int diagonal : DIAGONAL) {
                transform.matrix(diagonal, ONE);
            }
            transform.matrix(TRANSLATE_X, (section.position().sectionX() - anchor.sectionX()) * RtxSectionKey.SECTION_SIZE);
            transform.matrix(TRANSLATE_Y, (section.position().sectionY() - anchor.sectionY()) * RtxSectionKey.SECTION_SIZE);
            transform.matrix(TRANSLATE_Z, (section.position().sectionZ() - anchor.sectionZ()) * RtxSectionKey.SECTION_SIZE);
            long entry = table + (long) index * SURFACE_ENTRY_BYTES;
            MemoryUtil.memPutLong(entry, section.surfaceAddress());
            MemoryUtil.memPutInt(entry + SOLID_QUADS_OFFSET, section.solidQuads());
            instance.instanceCustomIndex(index)
                .mask(SECTION_RAYS)
                .flags(KHRAccelerationStructure.VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR)
                .accelerationStructureReference(section.address());
        }
    }

    void appendEntities(long address, float translateX, float translateY, float translateZ) {
        if (count + ENTITY_INSTANCES > CAPACITY) {
            return;
        }
        appendEntityInstance(address, translateX, translateY, translateZ, count, ENTITY_RAYS, KHRAccelerationStructure.VK_GEOMETRY_INSTANCE_FORCE_OPAQUE_BIT_KHR);
        appendEntityInstance(
            address, translateX, translateY, translateZ, ENTITY_REFLECTION_INDEX, ENTITY_REFLECTION_RAYS, KHRAccelerationStructure.VK_GEOMETRY_INSTANCE_FORCE_NO_OPAQUE_BIT_KHR
        );
    }

    private void appendEntityInstance(long address, float translateX, float translateY, float translateZ, int customIndex, int mask, int opacity) {
        VkAccelerationStructureInstanceKHR instance = VkAccelerationStructureInstanceKHR.create(instances[frame].mapped() + (long) count * VkAccelerationStructureInstanceKHR.SIZEOF);
        MemoryUtil.memSet(instance.address(), 0, VkAccelerationStructureInstanceKHR.SIZEOF);
        VkTransformMatrixKHR transform = instance.transform();
        for (int diagonal : DIAGONAL) {
            transform.matrix(diagonal, ONE);
        }
        transform.matrix(TRANSLATE_X, translateX);
        transform.matrix(TRANSLATE_Y, translateY);
        transform.matrix(TRANSLATE_Z, translateZ);
        instance.instanceCustomIndex(customIndex)
            .mask(mask)
            .flags(KHRAccelerationStructure.VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR | opacity)
            .accelerationStructureReference(address);
        count++;
    }

    void record(VkCommandBuffer commandBuffer) {
        structure.record(commandBuffer, RtxGeometry.instances(instances[frame].address(), count));
    }

    RtxBuffer surfaceTable() {
        return surfaceTables[frame];
    }

    long handle() {
        return structure.handle();
    }

    int count() {
        return count;
    }

    void release(GpuResources resources) {
        resources.release(tracked);
    }
}
