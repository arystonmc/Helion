package com.aryston.helion.render.temporal;

public record FrameSize(int internalWidth, int internalHeight, int outputWidth, int outputHeight) {
    public float upscaleRatio() {
        return (float) outputWidth / internalWidth;
    }

    public boolean upscales() {
        return internalWidth != outputWidth || internalHeight != outputHeight;
    }
}
