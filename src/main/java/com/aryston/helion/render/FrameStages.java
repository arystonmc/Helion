package com.aryston.helion.render;

import com.aryston.helion.render.atmosphere.SkyStage;
import com.aryston.helion.render.geometry.OpaqueGeometryStage;
import com.aryston.helion.render.geometry.TransparentGeometryStage;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.lighting.AmbientOcclusionStage;
import com.aryston.helion.render.post.BloomStage;
import com.aryston.helion.render.post.ImageCompositeStage;
import com.aryston.helion.render.post.PostProcessingStage;
import com.aryston.helion.render.post.PresentStage;
import com.aryston.helion.render.post.SharpeningStage;
import com.aryston.helion.render.scene.ClearStage;
import java.util.ArrayList;
import java.util.List;

public final class FrameStages {
    private FrameStages() {
    }

    public static void build(FrameContext frame, FrameSources sources) {
        create(frame, sources).stream()
            .filter(stage -> stage.isActive(frame))
            .forEach(stage -> stage.addTo(frame));
    }

    private static List<RenderStage> create(FrameContext frame, FrameSources sources) {
        HelionRenderCore core = HelionRenderCore.get();
        return List.of(
            new ClearStage(),
            new SkyStage(sources.atmosphere()),
            new OpaqueGeometryStage(sources.terrain(), sources.entities(), sources.atmosphere()),
            new AmbientOcclusionStage(core.ambientOcclusion()),
            new TransparentGeometryStage(sources.terrain(), sources.entities(), sources.atmosphere(), sources.transparency()),
            new PostProcessingStage(postEffects(sources, core)),
            output(frame, core)
        );
    }

    private static List<RenderStage> postEffects(FrameSources sources, HelionRenderCore core) {
        List<RenderStage> effects = new ArrayList<>(sources.postEffects());
        effects.add(new BloomStage(core.image()));
        effects.add(new SharpeningStage(core.image()));
        return effects;
    }

    private static RenderStage output(FrameContext frame, HelionRenderCore core) {
        ImageCompositeStage composite = new ImageCompositeStage(core.image());
        return composite.isActive(frame) ? composite : new PresentStage();
    }
}
