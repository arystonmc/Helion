package com.aryston.helion.render.atmosphere;

final class SkyViewCache {
    static final float LIGHT_HEIGHT_TOLERANCE = 2.5e-4F;
    static final float VIEW_HEIGHT_TOLERANCE = 0.001F;

    private float lightHeight;
    private float viewHeight;
    private boolean written;

    boolean isCurrent(float lightHeight, float viewHeight) {
        return written
            && Math.abs(lightHeight - this.lightHeight) <= LIGHT_HEIGHT_TOLERANCE
            && Math.abs(viewHeight - this.viewHeight) <= VIEW_HEIGHT_TOLERANCE;
    }

    void markWritten(float lightHeight, float viewHeight) {
        this.lightHeight = lightHeight;
        this.viewHeight = viewHeight;
        written = true;
    }

    void invalidate() {
        written = false;
    }
}
