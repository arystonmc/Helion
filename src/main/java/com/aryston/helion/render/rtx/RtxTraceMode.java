package com.aryston.helion.render.rtx;

enum RtxTraceMode {
    RAY_QUERY("rtx_ray_query"),
    GRID_WALK("rtx_grid_walk"),
    COMPARE("rtx_compare");

    private static final RtxTraceMode[] ROTATION = values();

    private final String stage;

    RtxTraceMode(String stage) {
        this.stage = stage;
    }

    String stage() {
        return stage;
    }

    static RtxTraceMode forFrame(long frame) {
        return ROTATION[(int) (frame % ROTATION.length)];
    }
}
