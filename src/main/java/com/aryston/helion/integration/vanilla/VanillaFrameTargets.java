package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.LevelRendererAccessor;
import com.aryston.helion.render.graph.FrameTargets;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.GpuFormat;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.util.ARGB;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

final class VanillaFrameTargets implements FrameTargets {
    private static final String SCENE_NAME = "helion:scene";
    private static final String OUTPUT_NAME = "main";
    private static final String ENTITY_OUTLINE_NAME = "entity_outline";

    private final LevelRendererAccessor level;
    private final LevelTargetBundle bundle;
    private final FeatureRenderDispatcher.PreparedFrame featureFrame;
    private final boolean orderIndependentTransparency;
    private final boolean writesAlwaysOnTopDepth;
    private ResourceHandle<RenderTarget> output;

    private VanillaFrameTargets(
        LevelRendererAccessor level,
        FeatureRenderDispatcher.PreparedFrame featureFrame,
        ResourceHandle<RenderTarget> output,
        boolean orderIndependentTransparency,
        boolean writesAlwaysOnTopDepth
    ) {
        this.level = level;
        this.bundle = level.helion$targets();
        this.featureFrame = featureFrame;
        this.output = output;
        this.orderIndependentTransparency = orderIndependentTransparency;
        this.writesAlwaysOnTopDepth = writesAlwaysOnTopDepth;
    }

    static VanillaFrameTargets create(
        FrameGraphBuilder builder,
        LevelRendererAccessor level,
        FeatureRenderDispatcher.PreparedFrame featureFrame,
        RenderTarget scene,
        RenderTarget output,
        boolean orderIndependentTransparency,
        boolean consistentDepthRequired
    ) {
        LevelTargetBundle bundle = level.helion$targets();
        bundle.main = builder.importExternal(SCENE_NAME, scene);
        ResourceHandle<RenderTarget> outputHandle = builder.importExternal(OUTPUT_NAME, output);
        if (orderIndependentTransparency) {
            createTransparencyTargets(builder, bundle, scene.width, scene.height);
        }
        boolean writesAlwaysOnTopDepth = consistentDepthRequired && hasAlwaysOnTop(level, featureFrame);
        if (writesAlwaysOnTopDepth) {
            bundle.alwaysOnTopDepth = builder.createInternal(
                "always_on_top_depth",
                new RenderTargetDescriptor(scene.width, scene.height, null, RenderTargetDescriptor.TextureProperties.DEFAULT_DEPTH)
            );
        }
        return new VanillaFrameTargets(level, featureFrame, outputHandle, orderIndependentTransparency, writesAlwaysOnTopDepth);
    }

    static boolean hasAlwaysOnTop(LevelRendererAccessor level, FeatureRenderDispatcher.PreparedFrame featureFrame) {
        return level.helion$frameHasAlwaysOnTopGizmos() || featureFrame.hasAnyAlwaysOnTop();
    }

    void importEntityOutline(FrameGraphBuilder builder) {
        bundle.entityOutline = builder.importExternal(ENTITY_OUTLINE_NAME, level.helion$entityOutlineTarget());
    }

    @Override
    public ResourceHandle<RenderTarget> scene() {
        return bundle.main;
    }

    @Override
    public void updateScene(ResourceHandle<RenderTarget> handle) {
        bundle.main = handle;
    }

    @Override
    public ResourceHandle<RenderTarget> output() {
        return output;
    }

    @Override
    public void updateOutput(ResourceHandle<RenderTarget> handle) {
        output = handle;
    }

    @Override
    public void declareGeometryAttachments(FramePass pass) {
        if (orderIndependentTransparency) {
            declareTransparencyTargets(pass);
        }
        if (writesAlwaysOnTopDepth) {
            bundle.alwaysOnTopDepth = pass.readsAndWrites(bundle.alwaysOnTopDepth);
        }
        if (level.helion$currentFrameRendersEntityOutline() && bundle.entityOutline != null) {
            bundle.entityOutline = pass.readsAndWrites(bundle.entityOutline);
        }
    }

    private void declareTransparencyTargets(FramePass pass) {
        bundle.depthBounds = pass.readsAndWrites(bundle.depthBounds);
        bundle.depthBoundsCulled = pass.readsAndWrites(bundle.depthBoundsCulled);
        for (int index = 0; index < LevelRenderer.OIT_TRANSMITTANCE_TARGET_COUNT; index++) {
            bundle.transmittance.set(index, pass.readsAndWrites(bundle.transmittance.get(index)));
        }
        bundle.accumulate = pass.readsAndWrites(bundle.accumulate);
        if (rendersClouds()) {
            bundle.oitCloudDepth = pass.readsAndWrites(bundle.oitCloudDepth);
        }
        if (featureFrame.hasAnyWaterMask()) {
            bundle.oitTerrainWithWaterPatchDepth = pass.readsAndWrites(bundle.oitTerrainWithWaterPatchDepth);
        }
    }

    private boolean rendersClouds() {
        return level.helion$optionsRenderState().cloudStatus != CloudStatus.OFF
            && ARGB.alpha(level.helion$levelRenderState().cloudColor) > 0;
    }

    private static void createTransparencyTargets(FrameGraphBuilder builder, LevelTargetBundle bundle, int width, int height) {
        RenderTargetDescriptor depthBounds = colorTarget(width, height, LevelRendererAccessor.helion$depthBoundsClearColor(), GpuFormat.RGBA32_FLOAT);
        RenderTargetDescriptor depthBoundsCulled = colorTarget(width, height, null, GpuFormat.RGBA32_FLOAT);
        RenderTargetDescriptor transmittance = colorTarget(width, height, LevelRendererAccessor.helion$zeroClearColor(), GpuFormat.RGBA16_FLOAT);
        RenderTargetDescriptor accumulate = colorTarget(width, height, LevelRendererAccessor.helion$zeroClearColor(), GpuFormat.RGBA16_FLOAT);
        RenderTargetDescriptor extraDepth = new RenderTargetDescriptor(
            width, height, null, new RenderTargetDescriptor.TextureProperties(null, GpuFormat.D32_FLOAT)
        );
        bundle.depthBounds = builder.createInternal("depth_bounds", depthBounds);
        bundle.depthBoundsCulled = builder.createInternal("depth_bounds_culled", depthBoundsCulled);
        for (int index = 0; index < LevelRenderer.OIT_TRANSMITTANCE_TARGET_COUNT; index++) {
            bundle.transmittance.set(index, builder.createInternal("transmittance", transmittance));
        }
        bundle.accumulate = builder.createInternal("accumulate", accumulate);
        bundle.oitCloudDepth = builder.createInternal("cloud_depth", extraDepth);
        bundle.oitTerrainWithWaterPatchDepth = builder.createInternal("terrain_depth", extraDepth);
    }

    private static RenderTargetDescriptor colorTarget(int width, int height, @Nullable Vector4fc clearColor, GpuFormat format) {
        return new RenderTargetDescriptor(width, height, new RenderTargetDescriptor.TextureProperties(clearColor, format), null);
    }
}
