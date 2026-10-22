package com.aryston.helion.render.rtx;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class RtxMaskViewStage implements RenderStage {
    private static final String NAME = "rtx_mask_view";

    private final RtxSpikeResources resources;
    private @Nullable CompiledRenderPipeline pipeline;
    private @Nullable RtxShadowMask mask;

    public RtxMaskViewStage(RtxSpikeResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!RtxSpike.VIEW || frame.scene().colorFormat() != RtxMaskPipelines.OUTPUT_FORMAT) {
            return false;
        }
        Optional<RtxShadowMask> current = resources.currentMask();
        Optional<CompiledRenderPipeline> compiled = HelionPipelines.compiled(RtxMaskPipelines.VIEW);
        mask = current.orElse(null);
        pipeline = compiled.orElse(null);
        return current.isPresent() && compiled.isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        CompiledRenderPipeline compiled = Objects.requireNonNull(pipeline);
        RtxShadowMask shown = Objects.requireNonNull(mask);
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> output = pass.readsAndWrites(frame.targets().output());
        frame.targets().updateOutput(output);
        pass.executes(frame.graph().timed(NAME, () ->
            FullscreenPass.draw("Helion RTX Spike Mask", output.get(), compiled, renderPass ->
                renderPass.setUniform(RtxMaskPipelines.MASK_SAMPLER, shown.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)))
        ));
    }
}
