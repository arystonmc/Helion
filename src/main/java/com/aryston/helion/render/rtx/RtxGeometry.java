package com.aryston.helion.render.rtx;

import org.lwjgl.vulkan.VK10;

record RtxGeometry(
    Kind kind,
    long dataAddress,
    long indexAddress,
    int vertexCount,
    int primitiveCount,
    boolean opaque,
    int vertexStride,
    int indexType,
    int primitiveOffset,
    int firstVertex
) {
    private static final long NO_ADDRESS = 0L;
    private static final int NO_VERTICES = 0;
    private static final int NO_OFFSET = 0;

    static RtxGeometry boxes(long address, int count) {
        return new RtxGeometry(Kind.BOXES, address, NO_ADDRESS, NO_VERTICES, count, true, RtxAccelerationStructure.BOX_STRIDE, VK10.VK_INDEX_TYPE_UINT32, NO_OFFSET, NO_OFFSET);
    }

    static RtxGeometry triangles(long vertexAddress, long indexAddress, int vertexCount, int triangleCount) {
        return triangles(vertexAddress, indexAddress, vertexCount, triangleCount, true);
    }

    static RtxGeometry triangles(long vertexAddress, long indexAddress, int vertexCount, int triangleCount, boolean opaque) {
        return new RtxGeometry(
            Kind.TRIANGLES, vertexAddress, indexAddress, vertexCount, triangleCount, opaque, RtxAccelerationStructure.VERTEX_STRIDE,
            VK10.VK_INDEX_TYPE_UINT32, NO_OFFSET, NO_OFFSET
        );
    }

    static RtxGeometry drawn(long positionAddress, int vertexStride, int vertexCount, long indexAddress, int indexType, int primitiveOffset, int firstVertex, int triangleCount) {
        return new RtxGeometry(Kind.TRIANGLES, positionAddress, indexAddress, vertexCount, triangleCount, true, vertexStride, indexType, primitiveOffset, firstVertex);
    }

    static RtxGeometry instances(long address, int count) {
        return new RtxGeometry(Kind.INSTANCES, address, NO_ADDRESS, NO_VERTICES, count, true, NO_OFFSET, VK10.VK_INDEX_TYPE_UINT32, NO_OFFSET, NO_OFFSET);
    }

    RtxGeometry withData(long address, int count) {
        return new RtxGeometry(kind, address, indexAddress, vertexCount, count, opaque, vertexStride, indexType, primitiveOffset, firstVertex);
    }

    enum Kind {
        BOXES,
        TRIANGLES,
        INSTANCES
    }
}
