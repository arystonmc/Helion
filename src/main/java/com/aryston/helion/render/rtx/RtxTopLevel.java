package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.aryston.helion.render.voxel.VoxelVolumeLayout;
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
    private static final int ALL_RAYS = 0xFF;
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
            surfaceTables[slot] = RtxBuffer.create(device, (long) CAPACITY * Long.BYTES, SURFACE_TABLE_USAGE, true);
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
            transform.matrix(TRANSLATE_X, (section.position().sectionX() - anchor.sectionX()) * VoxelVolumeLayout.SECTION_SIZE);
            transform.matrix(TRANSLATE_Y, (section.position().sectionY() - anchor.sectionY()) * VoxelVolumeLayout.SECTION_SIZE);
            transform.matrix(TRANSLATE_Z, (section.position().sectionZ() - anchor.sectionZ()) * VoxelVolumeLayout.SECTION_SIZE);
            MemoryUtil.memPutLong(table + (long) index * Long.BYTES, section.surfaceAddress());
            instance.instanceCustomIndex(index)
                .mask(ALL_RAYS)
                .flags(KHRAccelerationStructure.VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR)
                .accelerationStructureReference(section.address());
        }
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
