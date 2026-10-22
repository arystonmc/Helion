package com.aryston.helion.render.rtx;

public final class RtxSpike {
    public static final boolean ENABLED = Boolean.getBoolean("helion.rtxSpike");
    public static final boolean VIEW = ENABLED && Boolean.getBoolean("helion.rtxSpike.view");
    public static final int TEXTURE_USAGE_STORAGE = 1 << 16;

    private RtxSpike() {
    }
}
