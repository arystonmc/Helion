package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.LevelRendererAccessor;
import com.aryston.helion.render.FrameSources;
import com.aryston.helion.render.FrameStages;
import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderGraph;
import com.aryston.helion.render.graph.RenderSettings;
import com.aryston.helion.render.scene.SceneSnapshot;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

final class VanillaFrameDriver {
    private final SceneSkyRenderer sky = new SceneSkyRenderer();

    void render(LevelFrameRequest request, RenderSettings settings) {
        LevelRendererAccessor level = request.level();
        HelionRenderCore core = HelionRenderCore.get();
        RenderTarget output = level.helion$gameRenderer().mainRenderTarget();
        RenderTarget scene = core.beginLevelFrame(output);
        RenderSystem.isRenderingLevel = true;
        try {
            renderLevel(request, settings, scene, output);
            finishLevel(request);
        } finally {
            core.endLevelFrame();
            RenderSystem.isRenderingLevel = false;
        }
    }

    void close() {
        sky.close();
    }

    private void renderLevel(LevelFrameRequest request, RenderSettings settings, RenderTarget scene, RenderTarget output) {
        LevelRendererAccessor level = request.level();
        GameRenderer gameRenderer = level.helion$gameRenderer();
        LevelRenderState state = level.helion$levelRenderState();
        CameraRenderState camera = request.cameraState();
        ProfilerFiller profiler = Profiler.get();
        boolean orderIndependent = gameRenderer.useImprovedTransparency();
        level.helion$submitNodeStorage().setUseImprovedTransparency(orderIndependent);
        profiler.push("repositionCamera");
        level.helion$repositionCamera(camera);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(camera.viewRotationMatrix);
        FeatureRenderDispatcher.PreparedFrame featureFrame = null;
        try {
            profiler.popPush("submitFeatures");
            level.helion$submitFeatures(state, level.helion$submitNodeStorage(), request.renderOutline());
            profiler.popPush("prepareFeatures");
            featureFrame = level.helion$featureRenderDispatcher().prepareFrame(level.helion$submitNodeStorage());
            level.helion$setCurrentFrameRendersEntityOutline(featureFrame.hasAnyOutline() && state.shouldShowEntityOutlines);
            profiler.popPush("setupFrameGraph");
            FrameGraphBuilder builder = new FrameGraphBuilder();
            VanillaFrameTargets targets = VanillaFrameTargets.create(
                builder, level, featureFrame, scene, output, orderIndependent, request.consistentDepthRequired()
            );
            ClientHooks.fireFrameGraphSetup(builder, level.helion$targets(), camera, camera.viewRotationMatrix, profiler);
            targets.importEntityOutline(builder);
            ChunkSectionsToRender sections = prepareSections(request, orderIndependent);
            FrameContext frame = new FrameContext(
                new RenderGraph(builder, HelionRenderCore.get().timings()),
                targets,
                camera(camera),
                new SceneSnapshot(
                    request.fogColor(),
                    request.shouldRenderSky(),
                    orderIndependent,
                    scene.width,
                    scene.height,
                    Objects.requireNonNull(scene.getColorTexture()).getFormat()
                ),
                settings
            );
            HelionRenderCore.get().recordCamera(frame.camera());
            FrameStages.build(frame, sources(request, sections, featureFrame));
            profiler.popPush("executeFrameGraph");
            builder.execute(request.resourceAllocator(), new ProfilerInspector(profiler));
            profiler.pop();
        } finally {
            level.helion$targets().clear();
            modelView.popMatrix();
            if (featureFrame != null) {
                featureFrame.close();
            }
        }
    }

    private FrameSources sources(LevelFrameRequest request, ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame featureFrame) {
        StageEvents events = new StageEvents(request);
        return new FrameSources(
            new VanillaTerrainSource(request, sections, events),
            new VanillaEntitySource(request, featureFrame, events),
            new VanillaAtmosphereSource(request, sky, events),
            new VanillaTransparencySource(request, sections, featureFrame),
            List.of(new VanillaEntityOutlineEffect(request.level()))
        );
    }

    private static ChunkSectionsToRender prepareSections(LevelFrameRequest request, boolean orderIndependent) {
        LevelRendererAccessor level = request.level();
        LevelRenderer levelRenderer = request.levelRenderer();
        LevelRenderState state = level.helion$levelRenderState();
        Matrix4f terrainMatrix = new Matrix4f(state.cameraRenderState.viewRotationMatrix);
        boolean multiDrawIndirect = level.helion$multiDrawIndirectAvailable() && state.shouldUseMultiDrawIndirectForTerrain;
        level.helion$setUsingMultiDrawIndirectForTerrain(multiDrawIndirect);
        return multiDrawIndirect
            ? levelRenderer.prepareChunkRendersIndirect(terrainMatrix, !orderIndependent)
            : levelRenderer.prepareChunkRenders(terrainMatrix, !orderIndependent);
    }

    private static HelionCamera camera(CameraRenderState camera) {
        boolean zeroToOneDepth = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        Vector3d position = new Vector3d(camera.pos.x, camera.pos.y, camera.pos.z);
        return new HelionCamera(position, camera.viewRotationMatrix, camera.projectionMatrix, zeroToOneDepth);
    }

    private static void finishLevel(LevelFrameRequest request) {
        LevelRendererAccessor level = request.level();
        LevelRenderState state = level.helion$levelRenderState();
        CameraRenderState camera = request.cameraState();
        ProfilerFiller profiler = Profiler.get();
        profiler.push("compileSections");
        level.helion$compileSections(camera);
        profiler.pop();
        uploadTerrainBuffers(level.helion$sectionRenderDispatcher(), profiler);
        profiler.push("updateSectionOcclusion");
        level.helion$sectionOcclusionGraph().update(camera, level.helion$optionsRenderState().fov, state.chunkLoadingRenderState);
        profiler.pop();
        Runnable playerCompiledSectionCallback = state.playerCompiledSectionCallback;
        if (playerCompiledSectionCallback != null) {
            long fadeMillis = Util.toMillis(level.helion$optionsRenderState().chunkSectionFadeInTime);
            if (request.levelRenderer().isSectionCompiledAndVisible(state.cameraRenderState.blockPos, fadeMillis)) {
                playerCompiledSectionCallback.run();
            }
        }
    }

    private static void uploadTerrainBuffers(@Nullable SectionRenderDispatcher dispatcher, ProfilerFiller profiler) {
        if (dispatcher == null) {
            return;
        }
        dispatcher.lock();
        profiler.push("uploadTerrainBuffers");
        try {
            dispatcher.uploadTerrainBuffersToGpu();
        } finally {
            dispatcher.unlock();
        }
        profiler.pop();
    }

    private record ProfilerInspector(ProfilerFiller profiler) implements FrameGraphBuilder.Inspector {
        @Override
        public void beforeExecutePass(String name) {
            profiler.push(name);
        }

        @Override
        public void afterExecutePass(String name) {
            profiler.pop();
        }
    }
}
