package com.aryston.helion.render.atmosphere;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.Optional;

public interface AtmosphereSource {
    boolean hasSky();

    void renderSky(RenderTarget target);

    Optional<SkyEnvironment> skyEnvironment();

    void useFog(GpuBufferSlice fog);

    void renderCelestials(RenderTarget target);

    void prepareTranslucents();

    void renderClouds(RenderPass pass);

    void renderWeather(RenderPass pass);

    void renderWorldBorder(RenderPass pass);
}
