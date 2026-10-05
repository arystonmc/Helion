package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.LevelRendererAccessor;
import com.aryston.helion.render.geometry.EntitySource;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.util.profiling.Profiler;

final class VanillaEntitySource implements EntitySource {
    private final LevelRendererAccessor level;
    private final FeatureRenderDispatcher.PreparedFrame featureFrame;
    private final StageEvents events;
    private final boolean rendersAlwaysOnTop;
    private final boolean consistentDepthRequired;

    VanillaEntitySource(LevelFrameRequest request, FeatureRenderDispatcher.PreparedFrame featureFrame, StageEvents events) {
        this.level = request.level();
        this.featureFrame = featureFrame;
        this.events = events;
        this.rendersAlwaysOnTop = VanillaFrameTargets.hasAlwaysOnTop(level, featureFrame);
        this.consistentDepthRequired = request.consistentDepthRequired();
    }

    @Override
    public void prepareLighting() {
        level.helion$gameRenderer().lighting().setupFor(Lighting.Entry.LEVEL);
    }

    @Override
    public void renderSolid(RenderPass pass) {
        Profiler.get().push("renderSolidFeatures");
        featureFrame.executeSolid(pass);
        Profiler.get().pop();
        events.afterOpaqueFeatures(pass);
    }

    @Override
    public void renderTranslucent(RenderPass pass) {
        Profiler.get().push("renderTranslucentFeatures");
        featureFrame.executeTranslucent(pass);
        Profiler.get().pop();
        events.afterTranslucentFeatures(pass);
    }

    @Override
    public void renderTranslucentAfterTerrain(RenderPass pass) {
        featureFrame.executeTranslucentAfterTerrain(pass);
        events.afterTranslucentParticles(pass);
    }

    @Override
    public void renderOverlays(RenderTarget target) {
        level.helion$executeOutline(featureFrame);
        if (featureFrame.hasAnySeeThrough()) {
            level.helion$executeSeeThrough(featureFrame, target);
        }
        if (rendersAlwaysOnTop) {
            level.helion$executeAlwaysOnTop(featureFrame, target, consistentDepthRequired);
        }
    }
}
