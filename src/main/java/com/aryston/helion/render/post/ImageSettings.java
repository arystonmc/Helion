package com.aryston.helion.render.post;

public record ImageSettings(
    ToneMapper toneMapper,
    float exposure,
    boolean dither,
    BloomSettings bloom,
    SharpeningSettings sharpening
) {
    public static final float DEFAULT_EXPOSURE = 0.0F;
    public static final ImageSettings FOUNDATION = new ImageSettings(
        ToneMapper.NEUTRAL, DEFAULT_EXPOSURE, true, BloomSettings.DISABLED, SharpeningSettings.DISABLED
    );

    public boolean needsComposite() {
        return bloom.enabled() || sharpening.enabled() || exposure != DEFAULT_EXPOSURE || toneMapper == ToneMapper.FILMIC;
    }
}
