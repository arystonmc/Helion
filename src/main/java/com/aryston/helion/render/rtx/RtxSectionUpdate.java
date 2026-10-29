package com.aryston.helion.render.rtx;

import org.jspecify.annotations.Nullable;

public record RtxSectionUpdate(
    int sectionX, int sectionY, int sectionZ, float @Nullable [] positions, int @Nullable [] surface, int quads, int solidQuads, int translucentFrom, int[] lights
) {
    public static final int SURFACE_INTS_PER_VERTEX = 4;
    public static final int LIGHT_INTS = 8;
    private static final int NO_QUADS = 0;
    private static final int[] NO_LIGHTS = new int[0];

    public static RtxSectionUpdate mesh(
        int sectionX, int sectionY, int sectionZ, float[] positions, int[] surface, int quads, int solidQuads, int translucentFrom, int[] lights
    ) {
        return new RtxSectionUpdate(sectionX, sectionY, sectionZ, positions, surface, quads, solidQuads, translucentFrom, lights);
    }

    public static RtxSectionUpdate removed(int sectionX, int sectionY, int sectionZ) {
        return new RtxSectionUpdate(sectionX, sectionY, sectionZ, null, null, NO_QUADS, NO_QUADS, NO_QUADS, NO_LIGHTS);
    }

    boolean isRemoval() {
        return positions == null;
    }

    boolean isEmpty() {
        return quads == NO_QUADS;
    }
}
