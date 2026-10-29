package com.aryston.helion.render.rtx;

import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.mojang.blaze3d.framegraph.FramePass;
import java.util.List;
import org.joml.Vector3dc;

public final class RtxSceneStage implements RenderStage {
    static final String BOTTOM_STAGE = "rt_bottom_levels";
    static final String TOP_STAGE = "rt_top_level";

    private static final String NAME = "rt_scene";
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private final RayTracingResources resources;
    private final RtxLightSource lights;

    public RtxSceneStage(RayTracingResources resources, RtxLightSource lights) {
        this.resources = resources;
        this.lights = lights;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().rayTracing().active()) {
            resources.suspend();
            return false;
        }
        return resources.available();
    }

    @Override
    public void addTo(FrameContext frame) {
        long start = System.nanoTime();
        Vector3dc position = frame.camera().position();
        RtxSectionPos center = new RtxSectionPos(section(position.x()), section(position.y()), section(position.z()));
        RtxSectionScene scene = resources.scene();
        boolean tracesLight = frame.settings().rayTracing().tracesLight();
        RtxScenePlan plan = scene.prepare(
            resources.inbox(), center,
            tracesLight ? lights.lights(position.x(), position.y(), position.z()) : List.of(),
            tracesLight ? lights.ambientShare() : 1.0F
        );
        double prepareMillis = (System.nanoTime() - start) / NANOS_PER_MILLI;
        RtxDevice device = resources.device();
        FramePass pass = frame.graph().addPass(NAME);
        pass.disableCulling();
        Runnable bottom = frame.graph().timed(BOTTOM_STAGE, () -> {
            device.memoryBarrier();
            scene.recordBottomLevels(device.commandBuffer(), plan);
        });
        boolean withEntities = reflectsEntities(frame);
        resources.entityDraws().capture(withEntities);
        if (!withEntities) {
            scene.clearEntitySurfaces();
        }
        float translateX = (float) (position.x() - center.originX());
        float translateY = (float) (position.y() - center.originY());
        float translateZ = (float) (position.z() - center.originZ());
        Runnable top = frame.graph().timed(TOP_STAGE, () -> {
            device.memoryBarrier();
            if (withEntities) {
                scene.recordEntities(device.commandBuffer(), resources.entityDraws().take(), translateX, translateY, translateZ);
                device.memoryBarrier();
            }
            scene.recordTopLevel(device.commandBuffer());
            device.memoryBarrier();
        });
        pass.executes(() -> {
            if (!plan.builds().isEmpty()) {
                bottom.run();
            }
            top.run();
        });
        resources.logIfDue(plan, prepareMillis, HelionRenderCore.get().timings().averageMillis());
    }

    private boolean reflectsEntities(FrameContext frame) {
        return frame.settings().rayTracing().reflectsWater() && resources.device().entityTextures() && resources.reflectionAvailable();
    }

    private static int section(double coordinate) {
        return RtxSectionKey.sectionOf((int) Math.floor(coordinate));
    }
}
