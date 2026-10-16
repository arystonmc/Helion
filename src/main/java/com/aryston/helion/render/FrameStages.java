package com.aryston.helion.render;

import com.aryston.helion.render.atmosphere.PhysicalSkyStage;
import com.aryston.helion.render.atmosphere.SkyStage;
import com.aryston.helion.render.geometry.GeometryBufferDebugStage;
import com.aryston.helion.render.geometry.OpaqueGeometryStage;
import com.aryston.helion.render.geometry.SolidFeatureStage;
import com.aryston.helion.render.geometry.TransparentGeometryStage;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.lighting.AmbientOcclusionStage;
import com.aryston.helion.render.lighting.DeferredLightingStage;
import com.aryston.helion.render.dof.DepthOfFieldStage;
import com.aryston.helion.render.edge.EdgeSmoothingStage;
import com.aryston.helion.render.motion.MotionBlurStage;
import com.aryston.helion.render.post.BloomStage;
import com.aryston.helion.render.post.ExposureStage;
import com.aryston.helion.render.post.ImageCompositeStage;
import com.aryston.helion.render.post.PostProcessingStage;
import com.aryston.helion.render.post.PresentStage;
import com.aryston.helion.render.post.SharpeningStage;
import com.aryston.helion.render.scene.ClearStage;
import com.aryston.helion.render.shadow.ShadowStage;
import com.aryston.helion.render.temporal.TemporalStage;
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
        return List.of(
            new ClearStage(),
            sky(frame, sources, core),
            new OpaqueGeometryStage(sources.terrain(), sources.entities(), sources.atmosphere()),
            new ShadowStage(sources.shadowCasters(), core.shadows()),
            new DeferredLightingStage(core.lighting()),
            new SolidFeatureStage(sources.terrain(), sources.entities()),
            new AmbientOcclusionStage(core.ambientOcclusion()),
            new TransparentGeometryStage(sources.terrain(), sources.entities(), sources.atmosphere(), sources.transparency()),
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

    private static RenderStage sky(FrameContext frame, FrameSources sources, HelionRenderCore core) {
        PhysicalSkyStage physical = new PhysicalSkyStage(sources.atmosphere(), core.sky());
        return physical.isActive(frame) ? physical : new SkyStage(sources.atmosphere());
    }

    private static RenderStage output(FrameContext frame, HelionRenderCore core) {
        ImageCompositeStage composite = new ImageCompositeStage(core.image());
        return composite.isActive(frame) ? composite : new PresentStage();
    }
}
