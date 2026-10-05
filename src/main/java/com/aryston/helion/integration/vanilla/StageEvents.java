package com.aryston.helion.integration.vanilla;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4fc;

final class StageEvents {
    private final LevelRenderer levelRenderer;
    private final LevelRenderState state;

    StageEvents(LevelFrameRequest request) {
        this.levelRenderer = request.levelRenderer();
        this.state = request.level().helion$levelRenderState();
    }

    void afterSky() {
        NeoForge.EVENT_BUS.post(new RenderLevelStageEvent.AfterSky(levelRenderer, state, null, modelView(), levelRenderer.visibleSections()));
    }

    void afterOpaqueBlocks(RenderPass pass) {
        NeoForge.EVENT_BUS.post(new RenderLevelStageEvent.AfterOpaqueBlocks(levelRenderer, state, null, modelView(), levelRenderer.visibleSections(), pass));
    }

    void afterOpaqueFeatures(RenderPass pass) {
        NeoForge.EVENT_BUS.post(new RenderLevelStageEvent.AfterOpaqueFeatures(levelRenderer, state, null, modelView(), levelRenderer.visibleSections(), pass));
    }

    void afterTranslucentFeatures(RenderPass pass) {
        NeoForge.EVENT_BUS.post(new RenderLevelStageEvent.AfterTranslucentFeatures(levelRenderer, state, null, modelView(), levelRenderer.visibleSections(), pass));
    }

    void afterTranslucentBlocks(RenderPass pass) {
        NeoForge.EVENT_BUS.post(new RenderLevelStageEvent.AfterTranslucentBlocks(levelRenderer, state, null, modelView(), levelRenderer.visibleSections(), pass));
    }

    void afterTranslucentParticles(RenderPass pass) {
        NeoForge.EVENT_BUS.post(new RenderLevelStageEvent.AfterTranslucentParticles(levelRenderer, state, null, modelView(), levelRenderer.visibleSections(), pass));
    }

    void afterWeather(RenderPass pass) {
        NeoForge.EVENT_BUS.post(new RenderLevelStageEvent.AfterWeather(levelRenderer, state, null, modelView(), levelRenderer.visibleSections(), pass));
    }

    private Matrix4fc modelView() {
        return state.cameraRenderState.viewRotationMatrix;
    }
}
