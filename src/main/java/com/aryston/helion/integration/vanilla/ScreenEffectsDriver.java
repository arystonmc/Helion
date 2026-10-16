package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.FrameStages;
import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.atmosphere.AtmosphereResults;
import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderGraph;
import com.aryston.helion.render.graph.RenderSettings;
import com.aryston.helion.render.post.PostResults;
import com.aryston.helion.render.scene.SceneSnapshot;
import com.aryston.helion.render.shadow.ShadowResults;
import com.aryston.helion.render.temporal.FrameSize;
import com.aryston.helion.render.temporal.RenderScale;
import com.aryston.helion.render.temporal.TemporalFrame;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.Objects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

final class ScreenEffectsDriver {
    private static final String SCENE_NAME = "helion:scene";
    private static final String OUTPUT_NAME = "main";

    private final AmbientLightTracker ambientLight = new AmbientLightTracker();

    void render(LevelFrameRequest request, RenderSettings settings) {
        HelionRenderCore core = HelionRenderCore.get();
        GameRenderer gameRenderer = request.level().helion$gameRenderer();
        RenderTarget output = gameRenderer.mainRenderTarget();
        RenderTarget scene = core.beginLevelFrame(output, RenderScale.NATIVE);
        try {
            scene.copyColorFrom(output);
            scene.copyDepthFrom(output);
            FrameGraphBuilder builder = new FrameGraphBuilder();
            CameraRenderState cameraState = request.cameraState();
            HelionCamera camera = VanillaFrameDriver.camera(cameraState);
            TemporalFrame temporal = core.temporal()
                .begin(settings.temporal(), false, camera, new FrameSize(scene.width, scene.height, output.width, output.height));
            FrameContext frame = new FrameContext(
                new RenderGraph(builder, core.timings()),
                new ScreenTargets(builder.importExternal(SCENE_NAME, scene), builder.importExternal(OUTPUT_NAME, output)),
                camera,
                new SceneSnapshot(
                    request.fogColor(),
                    request.shouldRenderSky(),
                    gameRenderer.useImprovedTransparency(),
                    scene.width,
                    scene.height,
                    output.width,
                    output.height,
                    Objects.requireNonNull(scene.getColorTexture()).getFormat(),
                    ambientLight.update(cameraState.blockPos),
                    VanillaFrameDriver.lightEnvironment(gameRenderer.gameRenderState().lightmapRenderState)
                ),
                settings,
                new GeometryBuffer(),
                temporal,
                new AtmosphereResults(),
                new ShadowResults(),
                new PostResults()
            );
            core.recordFrame(frame.camera(), frame.scene());
            FrameStages.buildScreenEffects(frame);
            builder.execute(request.resourceAllocator());
        } finally {
            core.endLevelFrame();
        }
    }
}
