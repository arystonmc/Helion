package com.aryston.helion.render.rtx;

import org.jspecify.annotations.Nullable;

public record RtxSectionUpdate(
    int sectionX, int sectionY, int sectionZ, float @Nullable [] positions, int @Nullable [] surface, int quads, int solidQuads
) {
    public static final int SURFACE_INTS_PER_VERTEX = 3;
    private static final int NO_QUADS = 0;

    public static RtxSectionUpdate mesh(int sectionX, int sectionY, int sectionZ, float[] positions, int[] surface, int quads, int solidQuads) {
        return new RtxSectionUpdate(sectionX, sectionY, sectionZ, positions, surface, quads, solidQuads);
    }

    public static RtxSectionUpdate removed(int sectionX, int sectionY, int sectionZ) {
        return new RtxSectionUpdate(sectionX, sectionY, sectionZ, null, null, NO_QUADS, NO_QUADS);
    }

    boolean isRemoval() {
        return positions == null;
    }

    boolean isEmpty() {
        return quads == NO_QUADS;
    }
}
