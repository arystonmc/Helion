package com.aryston.helion.render.rtx;

public enum PathTracingQuality {
    LOW(2, 16, "HELION_PATH_QUALITY_LOW"),
    MEDIUM(2, 64, "HELION_PATH_QUALITY_MEDIUM"),
    HIGH(1, RtxLightCache.QUEUE_ROWS, "HELION_PATH_QUALITY_HIGH");

    private final int resolutionDivisor;
    private final int cacheRows;
    private final String define;

    PathTracingQuality(int resolutionDivisor, int cacheRows, String define) {
        this.resolutionDivisor = resolutionDivisor;
        this.cacheRows = cacheRows;
        this.define = define;
    }

    public int resolutionDivisor() {
        return resolutionDivisor;
    }

    int cacheRows() {
        return cacheRows;
    }

    String define() {
        return define;
    }
}
