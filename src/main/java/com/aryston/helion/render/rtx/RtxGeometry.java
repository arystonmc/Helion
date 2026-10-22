package com.aryston.helion.render.rtx;

record RtxGeometry(Kind kind, long dataAddress, long indexAddress, int vertexCount, int primitiveCount) {
    private static final long NO_ADDRESS = 0L;
    private static final int NO_VERTICES = 0;

    static RtxGeometry boxes(long address, int count) {
        return new RtxGeometry(Kind.BOXES, address, NO_ADDRESS, NO_VERTICES, count);
    }

    static RtxGeometry triangles(long vertexAddress, long indexAddress, int vertexCount, int triangleCount) {
        return new RtxGeometry(Kind.TRIANGLES, vertexAddress, indexAddress, vertexCount, triangleCount);
    }

    static RtxGeometry instances(long address, int count) {
        return new RtxGeometry(Kind.INSTANCES, address, NO_ADDRESS, NO_VERTICES, count);
    }

    RtxGeometry withData(long address, int count) {
        return new RtxGeometry(kind, address, indexAddress, vertexCount, count);
    }

    enum Kind {
        BOXES,
        TRIANGLES,
        INSTANCES
    }
}
