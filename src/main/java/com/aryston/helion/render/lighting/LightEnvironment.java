package com.aryston.helion.render.lighting;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public record LightEnvironment(
    float skyFactor,
    float blockFactor,
    float nightVisionFactor,
    float darknessScale,
    float bossOverlayDarkening,
    float brightness,
    Vector3fc blockLightTint,
    Vector3fc skyLightColor,
    Vector3fc ambientColor,
    Vector3fc nightVisionColor
) {
    public LightEnvironment {
        blockLightTint = new Vector3f(blockLightTint);
        skyLightColor = new Vector3f(skyLightColor);
        ambientColor = new Vector3f(ambientColor);
        nightVisionColor = new Vector3f(nightVisionColor);
    }
}
