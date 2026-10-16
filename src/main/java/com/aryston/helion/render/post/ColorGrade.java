package com.aryston.helion.render.post;

public enum ColorGrade {
    NEUTRAL(0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F),
    WARM(0.35F, 0.05F, 1.05F, 1.05F, 0.0F, 1.0F, 1.0F),
    COOL(-0.35F, -0.03F, 0.95F, 1.05F, 0.01F, 1.0F, 1.0F),
    VIVID(0.05F, 0.0F, 1.3F, 1.12F, 0.0F, 1.0F, 1.02F),
    CINEMATIC(0.15F, -0.05F, 0.9F, 1.15F, 0.02F, 1.05F, 0.98F),
    MUTED(0.0F, 0.0F, 0.7F, 0.95F, 0.03F, 1.0F, 0.98F);

    private final float temperature;
    private final float tint;
    private final float saturation;
    private final float contrast;
    private final float lift;
    private final float gamma;
    private final float gain;

    ColorGrade(float temperature, float tint, float saturation, float contrast, float lift, float gamma, float gain) {
        this.temperature = temperature;
        this.tint = tint;
        this.saturation = saturation;
        this.contrast = contrast;
        this.lift = lift;
        this.gamma = gamma;
        this.gain = gain;
    }

    public float temperature() {
        return temperature;
    }

    public float tint() {
        return tint;
    }

    public float saturation() {
        return saturation;
    }

    public float contrast() {
        return contrast;
    }

    public float lift() {
        return lift;
    }

    public float gamma() {
        return gamma;
    }

    public float gain() {
        return gain;
    }
}
