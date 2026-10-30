package com.aryston.helion.render;

import com.aryston.helion.render.atmosphere.PhysicalSkyStage;
import com.aryston.helion.render.atmosphere.SkyStage;
import com.aryston.helion.render.clouds.CloudStage;
import com.aryston.helion.render.geometry.EntityGeometryStage;
import com.aryston.helion.render.geometry.GeometryBufferDebugStage;
import com.aryston.helion.render.geometry.OpaqueGeometryStage;
import com.aryston.helion.render.geometry.SolidFeatureStage;
import com.aryston.helion.render.geometry.TranslucentFeatureStage;
import com.aryston.helion.render.geometry.TransparentGeometryStage;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.lightshaft.LightShaftStage;
import com.aryston.helion.render.dof.DepthOfFieldStage;
import com.aryston.helion.render.edge.EdgeSmoothingStage;
import com.aryston.helion.render.motion.MotionBlurStage;
import com.aryston.helion.render.post.BloomStage;
import com.aryston.helion.render.post.ExposureStage;
import com.aryston.helion.render.post.ImageCompositeStage;
import com.aryston.helion.render.post.PostProcessingStage;
import com.aryston.helion.render.post.PresentStage;
import com.aryston.helion.render.post.SharpeningStage;
import com.aryston.helion.render.rtx.RtxPathTracingStage;
import com.aryston.helion.render.rtx.RtxReflectionStage;
import com.aryston.helion.render.rtx.RtxSceneStage;
import com.aryston.helion.render.scene.ClearStage;
import com.aryston.helion.render.temporal.TemporalStage;
import com.aryston.helion.render.water.RainWetnessStage;
import com.aryston.helion.render.water.HeatShimmerStage;
import com.aryston.helion.render.water.ScreenReflectionStage;
import com.aryston.helion.render.water.UnderwaterStage;
import com.aryston.helion.render.water.WaterReflectionStage;
import com.aryston.helion.render.water.WaterSurfaceStage;
import com.aryston.helion.render.water.WaterWaveStage;
import java.util.ArrayList;
import java.util.List;

public final class FrameStages {
    private FrameStages() {
    }

    public static void build(FrameContext frame, FrameSources sources) {
        addActive(frame, create(frame, sources));
    }

    public static void buildScreenEffects(FrameContext frame) {
        HelionRenderCore core = HelionRenderCore.get();
        addActive(frame, List.of(new PostProcessingStage(postEffects(List.of(), core)), output(frame, core)));
    }

    private static void addActive(FrameContext frame, List<RenderStage> stages) {
        for (RenderStage stage : stages) {
            if (stage.isActive(frame)) {
                stage.addTo(frame);
            }
        }
    }

    private static List<RenderStage> create(FrameContext frame, FrameSources sources) {
        HelionRenderCore core = HelionRenderCore.get();
        PhysicalSkyStage physical = new PhysicalSkyStage(sources.atmosphere(), core.sky());
        return List.of(
            new ClearStage(),
            new RainWetnessStage(sources.weather(), sources.footprints(), sources.waves(), core.water()),
            sky(frame, physical, sources),
            new OpaqueGeometryStage(sources.terrain(), sources.entities(), sources.atmosphere(), core.glossy()),
            physical.backdrop(),
            new EntityGeometryStage(sources.entities()),
            new RtxSceneStage(core.rayTracing(), sources.lights()),
            new RtxPathTracingStage(core.rayTracing(), sources.terrain(), core.glossy()),
            new SolidFeatureStage(sources.terrain(), sources.entities()),
            new TranslucentFeatureStage(sources.entities()),
            new CloudStage(sources.clouds(), core.clouds()),
            new WaterWaveStage(sources.waves(), core.water()),
            new WaterSurfaceStage(sources.terrain(), core.water(), core.glossy()),
            new RtxReflectionStage(core.rayTracing(), sources.terrain()),
            new ScreenReflectionStage(core.water()),
            new WaterReflectionStage(core.water()),
            new LightShaftStage(core.lightShafts()),
            new TransparentGeometryStage(sources.terrain(), sources.entities(), sources.atmosphere(), sources.transparency(), core.glossy()),
            new HeatShimmerStage(core.water()),
            new UnderwaterStage(core.water()),
            new TemporalStage(core.temporal()),
            new PostProcessingStage(postEffects(sources.postEffects(), core)),
            output(frame, core),
            new GeometryBufferDebugStage()
        );
    }

    private static List<RenderStage> postEffects(List<RenderStage> sourceEffects, HelionRenderCore core) {
        List<RenderStage> effects = new ArrayList<>(sourceEffects);
        effects.add(new EdgeSmoothingStage());
        effects.add(new MotionBlurStage(core.motionBlur()));
        effects.add(new DepthOfFieldStage(core.depthOfField()));
        effects.add(new ExposureStage(core.image()));
        effects.add(new BloomStage(core.image()));
        effects.add(new SharpeningStage(core.image()));
        return effects;
    }

    private static RenderStage sky(FrameContext frame, PhysicalSkyStage physical, FrameSources sources) {
        return physical.isActive(frame) ? physical : new SkyStage(sources.atmosphere());
    }

    private static RenderStage output(FrameContext frame, HelionRenderCore core) {
        ImageCompositeStage composite = new ImageCompositeStage(core.image());
        return composite.isActive(frame) ? composite : new PresentStage();
    }
}
