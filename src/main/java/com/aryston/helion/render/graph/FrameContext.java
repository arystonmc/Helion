package com.aryston.helion.render.graph;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.scene.SceneSnapshot;

public record FrameContext(RenderGraph graph, FrameTargets targets, HelionCamera camera, SceneSnapshot scene) {
}
