package com.aryston.helion.render;

import com.aryston.helion.render.atmosphere.SkyStage;
import com.aryston.helion.render.geometry.GeometryStage;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.post.PostProcessingStage;
import com.aryston.helion.render.post.PresentStage;
import com.aryston.helion.render.scene.ClearStage;
import java.util.List;

public final class FrameStages {
    private FrameStages() {
    }

    public static void build(FrameContext frame, FrameSources sources) {
        create(sources).stream()
            .filter(stage -> stage.isActive(frame))
            .forEach(stage -> stage.addTo(frame));
    }

    private static List<RenderStage> create(FrameSources sources) {
        return List.of(
            new ClearStage(),
            new SkyStage(sources.atmosphere()),
            new GeometryStage(sources.terrain(), sources.entities(), sources.atmosphere(), sources.transparency()),
            new PostProcessingStage(sources.postEffects()),
            new PresentStage()
        );
    }
}
