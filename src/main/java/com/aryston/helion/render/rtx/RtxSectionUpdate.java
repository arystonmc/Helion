package com.aryston.helion.render.rtx;

import org.jspecify.annotations.Nullable;

public record RtxSectionUpdate(int sectionX, int sectionY, int sectionZ, float @Nullable [] positions, int quads) {
    private static final int NO_QUADS = 0;

    public static RtxSectionUpdate mesh(int sectionX, int sectionY, int sectionZ, float[] positions, int quads) {
        return new RtxSectionUpdate(sectionX, sectionY, sectionZ, positions, quads);
    }

    public static RtxSectionUpdate removed(int sectionX, int sectionY, int sectionZ) {
        return new RtxSectionUpdate(sectionX, sectionY, sectionZ, null, NO_QUADS);
    }

    boolean isRemoval() {
        return positions == null;
    }

    boolean isEmpty() {
        return quads == NO_QUADS;
    }
}
