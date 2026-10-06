package com.aryston.helion.render.graph;

import com.aryston.helion.render.atmosphere.AtmosphereResults;
import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.post.PostResults;
import com.aryston.helion.render.scene.SceneSnapshot;
import com.aryston.helion.render.temporal.TemporalFrame;

public record FrameContext(
    RenderGraph graph,
    FrameTargets targets,
    HelionCamera camera,
    SceneSnapshot scene,
    RenderSettings settings,
    GeometryBuffer geometry,
    TemporalFrame temporal,
    AtmosphereResults atmosphere,
    PostResults post
) {
}
