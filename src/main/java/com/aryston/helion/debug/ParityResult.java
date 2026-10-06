package com.aryston.helion.debug;

public record ParityResult(
    int pixels,
    int differentColorPixels,
    int maxColorDelta,
    int differentDepthPixels,
    long maxDepthDelta,
    int colorPixelsWithDifferentDepth
) {
    private static final int BITS_PER_BYTE = 8;

    public static ParityResult compare(byte[] colorA, byte[] colorB, int colorBlockSize, byte[] depthA, byte[] depthB, int depthBlockSize) {
        int pixels = Math.min(colorA.length, colorB.length) / colorBlockSize;
        int depthPixels = Math.min(depthA.length, depthB.length) / depthBlockSize;
        int differentColorPixels = 0;
        int maxColorDelta = 0;
        int differentDepthPixels = 0;
        long maxDepthDelta = 0;
        int colorPixelsWithDifferentDepth = 0;
        for (int pixel = 0; pixel < Math.max(pixels, depthPixels); pixel++) {
            int colorDelta = pixel < pixels ? colorDelta(colorA, colorB, pixel * colorBlockSize, colorBlockSize) : 0;
            long depthDelta = pixel < depthPixels ? depthDelta(depthA, depthB, pixel * depthBlockSize, depthBlockSize) : 0;
            if (colorDelta > 0) {
                differentColorPixels++;
                maxColorDelta = Math.max(maxColorDelta, colorDelta);
            }
            if (depthDelta > 0) {
                differentDepthPixels++;
                maxDepthDelta = Math.max(maxDepthDelta, depthDelta);
            }
            if (colorDelta > 0 && depthDelta > 0) {
                colorPixelsWithDifferentDepth++;
            }
        }
        return new ParityResult(pixels, differentColorPixels, maxColorDelta, differentDepthPixels, maxDepthDelta, colorPixelsWithDifferentDepth);
    }

    public boolean isIdentical() {
        return differentColorPixels == 0 && differentDepthPixels == 0;
    }

    static int colorDelta(byte[] first, byte[] second, int offset, int length) {
        int delta = 0;
        for (int index = offset; index < offset + length; index++) {
            delta = Math.max(delta, Math.abs(Byte.toUnsignedInt(first[index]) - Byte.toUnsignedInt(second[index])));
        }
        return delta;
    }

    static long depthDelta(byte[] first, byte[] second, int offset, int length) {
        return Math.abs(littleEndian(first, offset, length) - littleEndian(second, offset, length));
    }

    private static long littleEndian(byte[] bytes, int offset, int length) {
        long value = 0;
        for (int index = length - 1; index >= 0; index--) {
            value = (value << BITS_PER_BYTE) | Byte.toUnsignedLong(bytes[offset + index]);
        }
        return value;
    }
}
