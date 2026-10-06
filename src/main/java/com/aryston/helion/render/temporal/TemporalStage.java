package com.aryston.helion.render.temporal;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;

public final class TemporalStage implements RenderStage {
    private static final String NAME = "temporal_anti_aliasing";
    private static final String RESOLVE_PASS = "taa_resolve";
    private static final String APPLY_PASS = "taa_apply";
    private static final String PREVIOUS_HISTORY = "helion:taa_history_previous";
    private static final String NEXT_HISTORY = "helion:taa_history_next";

    private final TemporalResources resources;

    public TemporalStage(TemporalResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return frame.temporal().active() && frame.scene().colorFormat() == TemporalPipelines.SCENE_COLOR_FORMAT;
    }

    @Override
    public void addTo(FrameContext frame) {
        TemporalPrograms programs = resources.programs();
        ResourceHandle<RenderTarget> previous = frame.graph().builder().importExternal(PREVIOUS_HISTORY, resources.previousHistory());
        ResourceHandle<RenderTarget> next = frame.graph().builder().importExternal(NEXT_HISTORY, resources.nextHistory());
        ResourceHandle<RenderTarget> resolved = addResolvePass(frame, programs, previous, next);
        addApplyPass(frame, programs, resolved);
    }

    private ResourceHandle<RenderTarget> addResolvePass(
        FrameContext frame,
        TemporalPrograms programs,
        ResourceHandle<RenderTarget> previous,
        ResourceHandle<RenderTarget> next
    ) {
        FramePass pass = frame.graph().addPass(RESOLVE_PASS);
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        pass.reads(scene);
        pass.reads(previous);
        ResourceHandle<RenderTarget> resolved = pass.readsAndWrites(next);
        TemporalFrame temporal = frame.temporal();
        int width = frame.scene().width();
        int height = frame.scene().height();
        pass.executes(frame.graph().timed(RESOLVE_PASS, () -> FullscreenPass.draw(
            "Helion Temporal Resolve", resolved.get(), programs.resolve(), renderPass -> {
                renderPass.setUniform(TemporalPipelines.SETTINGS, resources.writeUniforms(temporal, width, height));
                renderPass.setUniform(TemporalPipelines.SCENE_COLOR_SAMPLER, colorView(scene), sampler(FilterMode.NEAREST));
                renderPass.setUniform(
                    TemporalPipelines.DEPTH_SAMPLER,
                    Objects.requireNonNull(scene.get().getDepthTextureView()),
                    sampler(FilterMode.NEAREST)
                );
                renderPass.setUniform(TemporalPipelines.HISTORY_SAMPLER, colorView(previous), sampler(FilterMode.LINEAR));
            })));
        return resolved;
    }

    private void addApplyPass(FrameContext frame, TemporalPrograms programs, ResourceHandle<RenderTarget> resolved) {
        FramePass pass = frame.graph().addPass(APPLY_PASS);
        pass.reads(resolved);
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        pass.executes(frame.graph().timed(APPLY_PASS, () -> {
            FullscreenPass.draw("Helion Temporal Apply", scene.get(), programs.apply(), renderPass ->
                renderPass.setUniform(TemporalPipelines.HISTORY_SAMPLER, colorView(resolved), sampler(FilterMode.NEAREST)));
            resources.finishFrame();
        }));
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }

    private static GpuSampler sampler(FilterMode filter) {
        return RenderSystem.getSamplerCache().getClampToEdge(filter);
    }
}
