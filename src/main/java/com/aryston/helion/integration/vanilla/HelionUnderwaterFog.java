package com.aryston.helion.integration.vanilla;

public final class HelionUnderwaterFog {
    private static final long VALID_NANOS = 1_000_000_000L;
    private static final float END_SCALE = 3.0F;
    private static final float MIN_END = 72.0F;

    private static volatile long extendedAt = Long.MIN_VALUE;

    private HelionUnderwaterFog() {
    }

    public static float extendedEnd(float vanillaEnd) {
        return System.nanoTime() - extendedAt < VALID_NANOS ? Math.max(vanillaEnd * END_SCALE, MIN_END) : vanillaEnd;
    }

    static void extend(boolean extend) {
        extendedAt = extend ? System.nanoTime() : Long.MIN_VALUE;
    }
}
