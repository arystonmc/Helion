package com.aryston.helion.debug;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.ARGB;

record ParityDifferenceImage(
    int width,
    int height,
    byte[] vanillaColor,
    byte[] helionColor,
    int colorBlockSize,
    byte[] vanillaDepth,
    byte[] helionDepth,
    int depthBlockSize
) {
    static final String FILE_NAME = "helion_parity_differences.png";
    private static final int COLOR_ONLY = ARGB.color(255, 255, 0, 0);
    private static final int COLOR_AND_DEPTH = ARGB.color(255, 255, 255, 0);
    private static final int DEPTH_ONLY = ARGB.color(255, 0, 96, 255);
    private static final float BACKGROUND_BRIGHTNESS = 0.25f;
    private static final float MAX_CHANNEL = 255.0f;
    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int BLUE = 2;
    private static final float RED_LUMA = 0.2126f;
    private static final float GREEN_LUMA = 0.7152f;
    private static final float BLUE_LUMA = 0.0722f;

    Path writeTo(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(FILE_NAME);
        try (NativeImage image = new NativeImage(width, height, false)) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixel(x, height - y - 1, pixel(x + y * width));
                }
            }
            image.writeToFile(file);
        }
        return file;
    }

    private int pixel(int index) {
        boolean color = ParityResult.colorDelta(vanillaColor, helionColor, index * colorBlockSize, colorBlockSize) > 0;
        boolean depth = ParityResult.depthDelta(vanillaDepth, helionDepth, index * depthBlockSize, depthBlockSize) > 0;
        if (color) {
            return depth ? COLOR_AND_DEPTH : COLOR_ONLY;
        }
        return depth ? DEPTH_ONLY : background(index * colorBlockSize);
    }

    private int background(int offset) {
        float luma = RED_LUMA * channel(offset + RED) + GREEN_LUMA * channel(offset + GREEN) + BLUE_LUMA * channel(offset + BLUE);
        return ARGB.gray(luma * BACKGROUND_BRIGHTNESS);
    }

    private float channel(int index) {
        return Byte.toUnsignedInt(vanillaColor[index]) / MAX_CHANNEL;
    }
}
