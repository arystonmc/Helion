package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.LevelRendererAccessor;
import com.aryston.helion.render.atmosphere.AtmosphereFog;
import com.aryston.helion.render.atmosphere.AtmosphereSource;
import com.aryston.helion.render.atmosphere.SkyEnvironment;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.Optional;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.CustomCloudsRenderer;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import net.neoforged.neoforge.client.CustomWeatherEffectRenderer;

final class VanillaAtmosphereSource implements AtmosphereSource {
    private static final int BLOCKS_PER_SECTION = 16;

    private final LevelRendererAccessor level;
    private final SceneSkyRenderer sky;
    private final SceneFog fog;
    private final StageEvents events;

    VanillaAtmosphereSource(LevelFrameRequest request, SceneSkyRenderer sky, SceneFog fog, StageEvents events) {
        this.level = request.level();
        this.sky = sky;
        this.fog = fog;
        this.events = events;
    }

    @Override
    public boolean hasSky() {
        return isSkyVisible(state().cameraRenderState) && state().skyRenderState.skybox != DimensionType.Skybox.NONE;
    }

    static boolean isSkyVisible(CameraRenderState camera) {
        boolean skyHiddenByFog = camera.fogType == FogType.POWDER_SNOW || camera.fogType == FogType.LAVA;
        return !skyHiddenByFog && !camera.entityRenderState.doesMobEffectBlockSky;
    }

    @Override
    public void renderSky(RenderTarget target) {
        LevelRenderState state = state();
        SkyRenderState skyState = state.skyRenderState;
        CustomSkyboxRenderer customSkybox = state.customSkyboxRenderer;
        boolean renderedByCustomSkybox = customSkybox != null
            && customSkybox.renderSky(state, skyState, state.cameraRenderState.viewRotationMatrix, fog.current());
        if (!renderedByCustomSkybox) {
            SkyRenderer renderer = sky.obtain(level, target, state.shouldResetSkyRenderer);
            HelionSky.draw(() -> renderer.render(fog.current(), skyState), false);
        }
        events.afterSky();
    }

    @Override
    public Optional<SkyEnvironment> skyEnvironment() {
        LevelRenderState state = state();
        SkyRenderState skyState = state.skyRenderState;
        ClientLevel clientLevel = Minecraft.getInstance().level;
        CameraRenderState camera = state.cameraRenderState;
        if (skyState.skybox != DimensionType.Skybox.OVERWORLD
            || state.customSkyboxRenderer != null
            || camera.fogType != FogType.NONE
            || clientLevel == null) {
            return Optional.empty();
        }
        float altitude = (float) (camera.pos.y - clientLevel.getSeaLevel());
        FogData fogData = camera.fogData;
        return Optional.of(new SkyEnvironment(
            SkyEnvironment.celestialDirection(skyState.sunAngle),
            SkyEnvironment.celestialDirection(skyState.moonAngle),
            skyState.rainBrightness,
            altitude,
            new AtmosphereFog(
                fogData.color,
                fogData.environmentalStart,
                fogData.environmentalEnd,
                fogData.renderDistanceStart,
                fogData.renderDistanceEnd,
                fogData.skyEnd,
                fogData.cloudEnd
            )
        ));
    }

    @Override
    public void useFog(GpuBufferSlice replacement) {
        fog.replace(replacement);
    }

    @Override
    public void renderCelestials(RenderTarget target) {
        LevelRenderState state = state();
        SkyRenderer renderer = sky.obtain(level, target, state.shouldResetSkyRenderer);
        HelionSky.draw(() -> renderer.render(fog.current(), state.skyRenderState), true);
        events.afterSky();
    }

    @Override
    public void prepareTranslucents() {
        level.helion$prepareTranslucents();
    }

    @Override
    public void renderClouds(RenderPass pass) {
        LevelRenderState state = state();
        CloudStatus cloudStatus = options().cloudStatus;
        if (cloudStatus == CloudStatus.OFF || ARGB.alpha(state.cloudColor) <= 0) {
            return;
        }
        CustomCloudsRenderer customClouds = state.customCloudsRenderer;
        boolean renderedByCustomClouds = customClouds != null
            && customClouds.renderClouds(state, cloudStatus, state.cameraRenderState.viewRotationMatrix, pass);
        if (!renderedByCustomClouds) {
            level.helion$cloudRenderer().render(cloudStatus, pass);
        }
    }

    @Override
    public void renderWeather(RenderPass pass) {
        LevelRenderState state = state();
        CustomWeatherEffectRenderer customWeather = state.customWeatherEffectRenderer;
        boolean renderedByCustomWeather = customWeather != null
            && customWeather.renderSnowAndRain(state, state.weatherRenderState, state.cameraRenderState.pos, pass);
        if (!renderedByCustomWeather) {
            level.helion$weatherEffectRenderer().render(state.weatherRenderState, pass);
        }
        events.afterWeather(pass);
    }

    @Override
    public void renderWorldBorder(RenderPass pass) {
        LevelRenderState state = state();
        int renderDistanceBlocks = options().renderDistance * BLOCKS_PER_SECTION;
        level.helion$worldBorderRenderer().render(state.worldBorderRenderState, pass, state.cameraRenderState.pos, renderDistanceBlocks);
    }

    private LevelRenderState state() {
        return level.helion$levelRenderState();
    }

    private OptionsRenderState options() {
        return level.helion$optionsRenderState();
    }
}
