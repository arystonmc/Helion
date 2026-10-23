package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;

final class RtxSectionBlas {
    private static final String LABEL = "Helion Ray Tracing Section";
    private static final String VERTICES_LABEL = "Helion Ray Tracing Section Vertices";
    private static final String SURFACE_LABEL = "Helion Ray Tracing Section Surface";
    private static final String SURFACE_UPLOAD_LABEL = "Helion Ray Tracing Section Surface Upload";
    private static final int VERTICES_PER_QUAD = 4;
    private static final int TRIANGLES_PER_QUAD = 2;
    private static final int USAGE = KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;
    private static final int SURFACE_USAGE = VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    private static final int UPLOAD_USAGE = VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
    private static final int SINGLE = 1;

    private final RtxSectionPos position;
    private final RtxAccelerationStructure structure;
    private final TrackedResource tracked;
    private final RtxBuffer surface;
    private final int solidQuads;
    private final TrackedResource trackedSurface;
    private @Nullable TrackedResource vertices;
    private @Nullable RtxBuffer upload;
    private @Nullable TrackedResource trackedUpload;

    private RtxSectionBlas(
        RtxSectionPos position,
        RtxAccelerationStructure structure,
        TrackedResource tracked,
        TrackedResource vertices,
        RtxBuffer surface,
        int solidQuads,
        TrackedResource trackedSurface,
        RtxBuffer upload,
        TrackedResource trackedUpload
    ) {
        this.position = position;
        this.structure = structure;
        this.tracked = tracked;
        this.vertices = vertices;
        this.surface = surface;
        this.solidQuads = solidQuads;
        this.trackedSurface = trackedSurface;
        this.upload = upload;
        this.trackedUpload = trackedUpload;
    }

    static RtxSectionBlas create(RtxDevice device, GpuResources resources, RtxSectionUpdate mesh, long indexAddress) {
        float[] positions = Objects.requireNonNull(mesh.positions());
        int[] attributes = Objects.requireNonNull(mesh.surface());
        int vertexCount = mesh.quads() * VERTICES_PER_QUAD;
        RtxBuffer vertexBuffer = RtxBuffer.create(device, (long) vertexCount * RtxAccelerationStructure.VERTEX_STRIDE, USAGE, true);
        MemoryUtil.memFloatBuffer(vertexBuffer.mapped(), vertexCount * RtxAccelerationStructure.VERTEX_FLOATS).put(positions, 0, vertexCount * RtxAccelerationStructure.VERTEX_FLOATS);
        TrackedResource vertices = resources.track(VERTICES_LABEL, vertexBuffer.size(), vertexBuffer::destroy);
        int surfaceInts = vertexCount * RtxSectionUpdate.SURFACE_INTS_PER_VERTEX;
        long surfaceBytes = (long) surfaceInts * Integer.BYTES;
        RtxBuffer upload = RtxBuffer.create(device, surfaceBytes, UPLOAD_USAGE, true);
        MemoryUtil.memIntBuffer(upload.mapped(), surfaceInts).put(attributes, 0, surfaceInts);
        TrackedResource trackedUpload = resources.track(SURFACE_UPLOAD_LABEL, upload.size(), upload::destroy);
        RtxBuffer surface = RtxBuffer.create(device, surfaceBytes, SURFACE_USAGE, false);
        TrackedResource trackedSurface = resources.track(SURFACE_LABEL, surface.size(), surface::destroy);
        int solidVertices = mesh.solidQuads() * VERTICES_PER_QUAD;
        List<RtxGeometry> geometries = List.of(
            RtxGeometry.triangles(vertexBuffer.address(), indexAddress, solidVertices, mesh.solidQuads() * TRIANGLES_PER_QUAD, true),
            RtxGeometry.triangles(
                vertexBuffer.address() + (long) solidVertices * RtxAccelerationStructure.VERTEX_STRIDE, indexAddress,
                vertexCount - solidVertices, (mesh.quads() - mesh.solidQuads()) * TRIANGLES_PER_QUAD, false
            )
        );
        RtxAccelerationStructure structure = RtxAccelerationStructure.createForSharedScratch(device, geometries);
        TrackedResource tracked = resources.track(LABEL, structure.bytes(), () -> structure.destroy(device.vkDevice()));
        return new RtxSectionBlas(
            new RtxSectionPos(mesh.sectionX(), mesh.sectionY(), mesh.sectionZ()), structure, tracked, vertices, surface, mesh.solidQuads(), trackedSurface, upload, trackedUpload
        );
    }

    long scratchSize() {
        return structure.scratchSize();
    }

    void record(VkCommandBuffer commandBuffer, GpuResources resources, long scratchAddress) {
        structure.recordWithScratch(commandBuffer, scratchAddress);
        if (upload != null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferCopy.Buffer region = VkBufferCopy.calloc(SINGLE, stack).size(surface.size());
                VK10.vkCmdCopyBuffer(commandBuffer, upload.buffer(), surface.buffer(), region);
            }
            upload = null;
        }
        releaseUploads(resources);
    }

    RtxSectionPos position() {
        return position;
    }

    long address() {
        return structure.address();
    }

    long surfaceAddress() {
        return surface.address();
    }

    int solidQuads() {
        return solidQuads;
    }

    long bytes() {
        return structure.bytes() + surface.size();
    }

    void release(GpuResources resources) {
        releaseUploads(resources);
        resources.release(trackedSurface);
        resources.release(tracked);
    }

    private void releaseUploads(GpuResources resources) {
        if (vertices != null) {
            resources.release(vertices);
            vertices = null;
        }
        if (trackedUpload != null) {
            resources.release(trackedUpload);
            trackedUpload = null;
            upload = null;
        }
    }
}
