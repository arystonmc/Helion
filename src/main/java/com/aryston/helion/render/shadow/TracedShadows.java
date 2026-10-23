package com.aryston.helion.render.shadow;

import com.aryston.helion.render.graph.FrameContext;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;

public interface TracedShadows {
    Optional<GpuTextureView> addTo(FrameContext frame, ShadowLight light);
}
