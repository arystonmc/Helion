package com.aryston.helion.render.atmosphere;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public record SkyLight(ResourceHandle<RenderTarget> irradiance, Vector3fc sunDirection, Vector3fc moonDirection) {
    public SkyLight {
        sunDirection = new Vector3f(sunDirection);
        moonDirection = new Vector3f(moonDirection);
    }
}
