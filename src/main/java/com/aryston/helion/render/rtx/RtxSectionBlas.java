package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.VkCommandBuffer;

final class RtxSectionBlas {
    private static final String LABEL = "Helion Ray Tracing Section";
    private static final String VERTICES_LABEL = "Helion Ray Tracing Section Vertices";
    private static final int VERTICES_PER_QUAD = 4;
    private static final int TRIANGLES_PER_QUAD = 2;
    private static final int USAGE = KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;

    private final RtxSectionPos position;
    private final RtxAccelerationStructure structure;
    private final TrackedResource tracked;
    private @Nullable TrackedResource vertices;

    private RtxSectionBlas(RtxSectionPos position, RtxAccelerationStructure structure, TrackedResource tracked, TrackedResource vertices) {
        this.position = position;
        this.structure = structure;
        this.tracked = tracked;
        this.vertices = vertices;
    }

    static RtxSectionBlas create(RtxDevice device, GpuResources resources, RtxSectionUpdate mesh, long indexAddress) {
        float[] positions = Objects.requireNonNull(mesh.positions());
        int vertexCount = mesh.quads() * VERTICES_PER_QUAD;
        RtxBuffer vertexBuffer = RtxBuffer.create(device, (long) vertexCount * RtxAccelerationStructure.VERTEX_STRIDE, USAGE, true);
        MemoryUtil.memFloatBuffer(vertexBuffer.mapped(), vertexCount * RtxAccelerationStructure.VERTEX_FLOATS).put(positions, 0, vertexCount * RtxAccelerationStructure.VERTEX_FLOATS);
        TrackedResource vertices = resources.track(VERTICES_LABEL, vertexBuffer.size(), vertexBuffer::destroy);
        RtxGeometry geometry = RtxGeometry.triangles(vertexBuffer.address(), indexAddress, vertexCount, mesh.quads() * TRIANGLES_PER_QUAD);
        RtxAccelerationStructure structure = RtxAccelerationStructure.createForSharedScratch(device, geometry);
        TrackedResource tracked = resources.track(LABEL, structure.bytes(), () -> structure.destroy(device.vkDevice()));
        return new RtxSectionBlas(new RtxSectionPos(mesh.sectionX(), mesh.sectionY(), mesh.sectionZ()), structure, tracked, vertices);
    }

    long scratchSize() {
        return structure.scratchSize();
    }

    void record(VkCommandBuffer commandBuffer, GpuResources resources, long scratchAddress) {
        structure.recordWithScratch(commandBuffer, scratchAddress);
        if (vertices != null) {
            resources.release(vertices);
            vertices = null;
        }
    }

    RtxSectionPos position() {
        return position;
    }

    long address() {
        return structure.address();
    }

    long bytes() {
        return structure.bytes();
    }

    void release(GpuResources resources) {
        if (vertices != null) {
            resources.release(vertices);
            vertices = null;
        }
        resources.release(tracked);
    }
}
