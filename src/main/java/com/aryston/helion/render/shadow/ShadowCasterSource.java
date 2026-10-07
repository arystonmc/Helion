package com.aryston.helion.render.shadow;

import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.List;

public interface ShadowCasterSource {
    boolean supportsShadows();

    int renderDistanceBlocks();

    void prepare(List<ShadowCascade> cascades);

    void renderCascade(int cascade, RenderPass pass);
}
