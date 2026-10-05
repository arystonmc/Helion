package com.aryston.helion.render.scene;

import org.joml.Vector4f;
import org.joml.Vector4fc;

public record SceneSnapshot(Vector4fc fogColor, boolean renderSky, boolean orderIndependentTransparency, int width, int height) {
    public SceneSnapshot {
        fogColor = new Vector4f(fogColor);
    }
}
