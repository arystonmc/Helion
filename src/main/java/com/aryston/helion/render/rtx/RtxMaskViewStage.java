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
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class RtxMaskViewStage implements RenderStage {
    private final String name;
    private final boolean enabled;
    private final RenderPipeline view;
    private final Supplier<Optional<RtxShadowMask>> masks;
    private @Nullable CompiledRenderPipeline pipeline;
    private @Nullable RtxShadowMask mask;

    private RtxMaskViewStage(String name, boolean enabled, RenderPipeline view, Supplier<Optional<RtxShadowMask>> masks) {
        this.name = name;
        this.enabled = enabled;
        this.view = view;
        this.masks = masks;
    }

    public static RtxMaskViewStage spike(RtxSpikeResources resources) {
        return new RtxMaskViewStage("rtx_mask_view", RtxSpike.VIEW, RtxMaskPipelines.VIEW, resources::currentMask);
    }

    public static RtxMaskViewStage sunShadow(RayTracingResources resources) {
        return new RtxMaskViewStage("rt_sun_shadow_view", RayTracing.VIEW, RtxMaskPipelines.SUN_VIEW, resources::currentMask);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!enabled || frame.scene().colorFormat() != RtxMaskPipelines.OUTPUT_FORMAT) {
            return false;
        }
        Optional<RtxShadowMask> current = masks.get();
        Optional<CompiledRenderPipeline> compiled = HelionPipelines.compiled(view);
        mask = current.orElse(null);
        pipeline = compiled.orElse(null);
        return current.isPresent() && compiled.isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        CompiledRenderPipeline compiled = Objects.requireNonNull(pipeline);
        RtxShadowMask shown = Objects.requireNonNull(mask);
        FramePass pass = frame.graph().addPass(name);
        ResourceHandle<RenderTarget> output = pass.readsAndWrites(frame.targets().output());
        frame.targets().updateOutput(output);
        pass.executes(frame.graph().timed(name, () ->
            FullscreenPass.draw(name, output.get(), compiled, renderPass ->
                renderPass.setUniform(RtxMaskPipelines.MASK_SAMPLER, shown.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)))
        ));
    }
}
