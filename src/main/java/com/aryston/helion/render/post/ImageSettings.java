package com.aryston.helion.render.post;

public record ImageSettings(
    ToneMapper toneMapper,
    float exposure,
    boolean autoExposure,
    boolean dither,
    BloomSettings bloom,
    SharpeningSettings sharpening,
    MotionBlurSettings motionBlur,
    ColorGrade colorGrade
) {
    public static final float DEFAULT_EXPOSURE = 0.0F;
    public static final ImageSettings FOUNDATION = new ImageSettings(
        ToneMapper.NEUTRAL, DEFAULT_EXPOSURE, false, true, BloomSettings.DISABLED, SharpeningSettings.DISABLED, MotionBlurSettings.DISABLED,
        ColorGrade.NEUTRAL
    );

    public boolean needsComposite() {
        return bloom.enabled() || sharpening.enabled() || autoExposure || exposure != DEFAULT_EXPOSURE || toneMapper == ToneMapper.FILMIC
            || colorGrade != ColorGrade.NEUTRAL;
    }
}
