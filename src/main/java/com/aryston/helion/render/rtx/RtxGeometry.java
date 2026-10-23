package com.aryston.helion.render.rtx;

record RtxGeometry(Kind kind, long dataAddress, long indexAddress, int vertexCount, int primitiveCount, boolean opaque) {
    private static final long NO_ADDRESS = 0L;
    private static final int NO_VERTICES = 0;

    static RtxGeometry boxes(long address, int count) {
        return new RtxGeometry(Kind.BOXES, address, NO_ADDRESS, NO_VERTICES, count, true);
    }

    static RtxGeometry triangles(long vertexAddress, long indexAddress, int vertexCount, int triangleCount) {
        return triangles(vertexAddress, indexAddress, vertexCount, triangleCount, true);
    }

    static RtxGeometry triangles(long vertexAddress, long indexAddress, int vertexCount, int triangleCount, boolean opaque) {
        return new RtxGeometry(Kind.TRIANGLES, vertexAddress, indexAddress, vertexCount, triangleCount, opaque);
    }

    static RtxGeometry instances(long address, int count) {
        return new RtxGeometry(Kind.INSTANCES, address, NO_ADDRESS, NO_VERTICES, count, true);
    }

    RtxGeometry withData(long address, int count) {
        return new RtxGeometry(kind, address, indexAddress, vertexCount, count, opaque);
    }

    enum Kind {
        BOXES,
        TRIANGLES,
        INSTANCES
    }
}
