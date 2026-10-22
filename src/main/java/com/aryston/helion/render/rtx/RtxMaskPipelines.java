package com.aryston.helion.render.rtx;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;

public final class RtxMaskPipelines {
    public static final GpuFormat OUTPUT_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final String MASK_SAMPLER = "ShadowMaskSampler";

    public static final RenderPipeline VIEW = HelionPipelines.fullscreen("rtx/mask_view")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(MASK_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), OUTPUT_FORMAT, ColorTargetState.WRITE_ALL))
        .build();

    private RtxMaskPipelines() {
    }

    public static List<RenderPipeline> all() {
        return RtxSpike.VIEW ? List.of(VIEW) : List.of();
    }
}
