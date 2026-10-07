package com.aryston.helion.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuSampler;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.resources.Identifier;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("DEPTH_BOUNDS_CLEAR_COLOR")
    static Vector4fc helion$depthBoundsClearColor() {
        throw new AssertionError();
    }

    @Accessor("ZERO_CLEAR_COLOR")
    static Vector4fc helion$zeroClearColor() {
        throw new AssertionError();
    }

    @Accessor("ENTITY_OUTLINE_POST_CHAIN_ID")
    static Identifier helion$entityOutlinePostChainId() {
        throw new AssertionError();
    }

    @Accessor("gameRenderer")
    GameRenderer helion$gameRenderer();

    @Accessor("submitNodeStorage")
    SubmitNodeStorage helion$submitNodeStorage();

    @Accessor("featureRenderDispatcher")
    FeatureRenderDispatcher helion$featureRenderDispatcher();

    @Accessor("levelRenderState")
    LevelRenderState helion$levelRenderState();

    @Accessor("optionsRenderState")
    OptionsRenderState helion$optionsRenderState();

    @Accessor("textureManager")
    TextureManager helion$textureManager();

    @Accessor("atlasManager")
    AtlasManager helion$atlasManager();

    @Accessor("shaderManager")
    ShaderManager helion$shaderManager();

    @Accessor("cloudRenderer")
    CloudRenderer helion$cloudRenderer();

    @Accessor("worldBorderRenderer")
    WorldBorderRenderer helion$worldBorderRenderer();

    @Accessor("weatherEffectRenderer")
    WeatherEffectRenderer helion$weatherEffectRenderer();

    @Accessor("sectionOcclusionGraph")
    SectionOcclusionGraph helion$sectionOcclusionGraph();

    @Accessor("entityOutlineTarget")
    RenderTarget helion$entityOutlineTarget();

    @Accessor("targets")
    LevelTargetBundle helion$targets();

    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> helion$visibleSections();

    @Accessor("viewArea")
    @Nullable ViewArea helion$viewArea();

    @Accessor("sectionRenderDispatcher")
    @Nullable SectionRenderDispatcher helion$sectionRenderDispatcher();

    @Accessor("skyRenderer")
    void helion$setSkyRenderer(SkyRenderer renderer);

    @Accessor("chunkLayerSampler")
    @Nullable GpuSampler helion$chunkLayerSampler();

    @Accessor("chunkLayerSampler")
    void helion$setChunkLayerSampler(@Nullable GpuSampler sampler);

    @Accessor("currentFrameRendersEntityOutline")
    boolean helion$currentFrameRendersEntityOutline();

    @Accessor("currentFrameRendersEntityOutline")
    void helion$setCurrentFrameRendersEntityOutline(boolean rendersEntityOutline);

    @Accessor("multiDrawIndirectAvailable")
    boolean helion$multiDrawIndirectAvailable();

    @Accessor("usingMultiDrawIndirectForTerrain")
    boolean helion$usingMultiDrawIndirectForTerrain();

    @Accessor("usingMultiDrawIndirectForTerrain")
    void helion$setUsingMultiDrawIndirectForTerrain(boolean usingMultiDrawIndirect);

    @Invoker("repositionCamera")
    void helion$repositionCamera(CameraRenderState camera);

    @Invoker("submitFeatures")
    void helion$submitFeatures(LevelRenderState levelRenderState, SubmitNodeCollector collector, boolean renderOutline);

    @Invoker("frameHasAlwaysOnTopGizmos")
    boolean helion$frameHasAlwaysOnTopGizmos();

    @Invoker("prepareTranslucents")
    void helion$prepareTranslucents();

    @Invoker("executeOit")
    void helion$executeOit(ChunkSectionsToRender chunkSections, FeatureRenderDispatcher.PreparedFrame featureFrame);

    @Invoker("executeOutline")
    void helion$executeOutline(FeatureRenderDispatcher.PreparedFrame featureFrame);

    @Invoker("executeSeeThrough")
    void helion$executeSeeThrough(FeatureRenderDispatcher.PreparedFrame featureFrame, RenderTarget mainTarget);

    @Invoker("executeAlwaysOnTop")
    void helion$executeAlwaysOnTop(FeatureRenderDispatcher.PreparedFrame featureFrame, RenderTarget mainTarget, boolean consistentDepthRequired);

    @Invoker("compileSections")
    void helion$compileSections(CameraRenderState camera);
}
