package com.aryston.helion.debug;

public record ParityResult(int pixels, int differentColorPixels, int maxColorDelta, int differentDepthPixels) {
    public static ParityResult compare(byte[] colorA, byte[] colorB, int colorBlockSize, byte[] depthA, byte[] depthB, int depthBlockSize) {
        int pixels = Math.min(colorA.length, colorB.length) / colorBlockSize;
        int differentColorPixels = 0;
        int maxColorDelta = 0;
        for (int pixel = 0; pixel < pixels; pixel++) {
            int delta = maxDelta(colorA, colorB, pixel * colorBlockSize, colorBlockSize);
            if (delta > 0) {
                differentColorPixels++;
                maxColorDelta = Math.max(maxColorDelta, delta);
            }
        }
        int depthPixels = Math.min(depthA.length, depthB.length) / depthBlockSize;
        int differentDepthPixels = 0;
        for (int pixel = 0; pixel < depthPixels; pixel++) {
            if (maxDelta(depthA, depthB, pixel * depthBlockSize, depthBlockSize) > 0) {
                differentDepthPixels++;
            }
        }
        return new ParityResult(pixels, differentColorPixels, maxColorDelta, differentDepthPixels);
    }

    public boolean isIdentical() {
        return differentColorPixels == 0 && differentDepthPixels == 0;
    }

    private static int maxDelta(byte[] first, byte[] second, int offset, int length) {
        int delta = 0;
        for (int index = offset; index < offset + length; index++) {
            delta = Math.max(delta, Math.abs(Byte.toUnsignedInt(first[index]) - Byte.toUnsignedInt(second[index])));
        }
        return delta;
    }
}
