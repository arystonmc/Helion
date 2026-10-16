package com.aryston.helion.render.scene;

import com.aryston.helion.render.lighting.LightEnvironment;
import com.aryston.helion.render.temporal.FrameSize;
import com.mojang.renderpearl.api.GpuFormat;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public record SceneSnapshot(
    Vector4fc fogColor,
    boolean renderSky,
    boolean orderIndependentTransparency,
    int width,
    int height,
    int outputWidth,
    int outputHeight,
    GpuFormat colorFormat,
    float ambientLight,
    LightEnvironment light
) {
    public SceneSnapshot {
        fogColor = new Vector4f(fogColor);
    }

    public FrameSize size() {
        return new FrameSize(width, height, outputWidth, outputHeight);
    }
}
