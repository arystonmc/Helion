package com.aryston.helion.render.temporal;

public enum FrameRateTarget {
    FPS_60(60),
    FPS_90(90),
    FPS_120(120),
    FPS_144(144),
    FPS_165(165),
    FPS_200(200),
    FPS_240(240);

    private final int framesPerSecond;

    FrameRateTarget(int framesPerSecond) {
        this.framesPerSecond = framesPerSecond;
    }

    public int framesPerSecond() {
        return framesPerSecond;
    }
}
