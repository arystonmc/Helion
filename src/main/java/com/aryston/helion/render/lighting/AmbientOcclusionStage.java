package com.aryston.helion.render.lighting;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class AmbientOcclusionStage implements RenderStage {
    private static final String NAME = "ambient_occlusion";
    private static final String VIEW_DEPTH_PASS = "ao_depth";
    private static final String HORIZON_PASS = "ao_main";
    private static final String DENOISE_PASS = "ao_denoise_";
    private static final String APPLY_PASS = "ao_apply";

    private final AmbientOcclusionResources resources;
    private @Nullable AmbientOcclusionPrograms programs;
    private @Nullable GpuBuffer frameUniforms;

    public AmbientOcclusionStage(AmbientOcclusionResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().ambientOcclusion().enabled()
            || frame.scene().colorFormat() != AmbientOcclusionPipelines.SCENE_COLOR_FORMAT) {
            return false;
        }
        Optional<AmbientOcclusionPrograms> compiled = AmbientOcclusionPrograms.compile();
        if (compiled.isEmpty()) {
            resources.reportMissingPipelines();
            return false;
        }
        programs = compiled.get();
        return true;
    }

    @Override
    public void addTo(FrameContext frame) {
        AmbientOcclusionPrograms compiled = Objects.requireNonNull(programs);
        AmbientOcclusionSettings settings = frame.settings().ambientOcclusion();
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        ResourceHandle<RenderTarget> viewDepth = addViewDepthPass(frame, compiled, scene);
        ResourceHandle<RenderTarget> occlusionAndEdges = addHorizonSearchPass(frame, compiled, settings, viewDepth);
        ResourceHandle<RenderTarget> occlusion = occlusionAndEdges;
        int passes = settings.quality().denoisePasses();
        for (int index = 1; index <= passes; index++) {
            occlusion = addDenoisePass(frame, compiled, index, index == passes, occlusion, occlusionAndEdges, viewDepth, scene);
        }
        addApplyPass(frame, compiled, settings.debugView(), occlusion);
    }

    private ResourceHandle<RenderTarget> addViewDepthPass(FrameContext frame, AmbientOcclusionPrograms compiled, ResourceHandle<RenderTarget> scene) {
        FramePass pass = frame.graph().addPass(VIEW_DEPTH_PASS);
        pass.reads(scene);
        ResourceHandle<RenderTarget> viewDepth = pass.createsInternal(
            "helion:ao_view_depth", target(frame, AmbientOcclusionPipelines.VIEW_DEPTH_FORMAT)
        );
        pass.executes(frame.graph().timed(VIEW_DEPTH_PASS, () -> FullscreenPass.draw(
            "Helion AO View Depth", viewDepth.get(), compiled.viewDepth(), renderPass -> {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform(
                    AmbientOcclusionPipelines.DEPTH_SAMPLER,
                    Objects.requireNonNull(scene.get().getDepthTextureView()),
                    nearest()
                );
            })));
        return viewDepth;
    }

    private ResourceHandle<RenderTarget> addHorizonSearchPass(
        FrameContext frame,
        AmbientOcclusionPrograms compiled,
        AmbientOcclusionSettings settings,
        ResourceHandle<RenderTarget> viewDepth
    ) {
        FramePass pass = frame.graph().addPass(HORIZON_PASS);
        pass.reads(viewDepth);
        ResourceHandle<RenderTarget> occlusionAndEdges = pass.createsInternal(
            "helion:ao_raw", target(frame, AmbientOcclusionPipelines.OCCLUSION_AND_EDGES_FORMAT)
        );
        int width = frame.scene().width();
        int height = frame.scene().height();
        pass.executes(frame.graph().timed(HORIZON_PASS, () -> {
            frameUniforms = resources.writeUniforms(settings, width, height);
            FullscreenPass.draw("Helion AO Horizon Search", occlusionAndEdges.get(), compiled.horizonSearch(), renderPass -> {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform(AmbientOcclusionPipelines.SETTINGS, Objects.requireNonNull(frameUniforms));
                renderPass.setUniform(AmbientOcclusionPipelines.VIEW_DEPTH_SAMPLER, colorView(viewDepth), nearest());
            });
        }));
        return occlusionAndEdges;
    }

    private ResourceHandle<RenderTarget> addDenoisePass(
        FrameContext frame,
        AmbientOcclusionPrograms compiled,
        int index,
        boolean resolve,
        ResourceHandle<RenderTarget> occlusion,
        ResourceHandle<RenderTarget> occlusionAndEdges,
        ResourceHandle<RenderTarget> viewDepth,
        ResourceHandle<RenderTarget> scene
    ) {
        String name = DENOISE_PASS + index;
        FramePass pass = frame.graph().addPass(name);
        pass.reads(occlusion);
        if (occlusion != occlusionAndEdges) {
            pass.reads(occlusionAndEdges);
        }
        if (resolve) {
            pass.reads(viewDepth);
            pass.reads(scene);
        }
        ResourceHandle<RenderTarget> output = pass.createsInternal(
            "helion:ao_denoised_" + index, target(frame, AmbientOcclusionPipelines.OCCLUSION_FORMAT)
        );
        CompiledRenderPipeline pipeline = resolve ? compiled.denoiseAndResolve() : compiled.denoise();
        pass.executes(frame.graph().timed(name, () -> FullscreenPass.draw("Helion AO Denoise", output.get(), pipeline, renderPass -> {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform(AmbientOcclusionPipelines.SETTINGS, Objects.requireNonNull(frameUniforms));
            renderPass.setUniform(AmbientOcclusionPipelines.OCCLUSION_SAMPLER, colorView(occlusion), nearest());
            renderPass.setUniform(AmbientOcclusionPipelines.EDGES_SAMPLER, colorView(occlusionAndEdges), nearest());
            if (resolve) {
                renderPass.setUniform(AmbientOcclusionPipelines.VIEW_DEPTH_SAMPLER, colorView(viewDepth), nearest());
                renderPass.setUniform(AmbientOcclusionPipelines.SCENE_COLOR_SAMPLER, colorView(scene), nearest());
            }
        })));
        return output;
    }

    private void addApplyPass(FrameContext frame, AmbientOcclusionPrograms compiled, boolean debugView, ResourceHandle<RenderTarget> occlusion) {
        FramePass pass = frame.graph().addPass(APPLY_PASS);
        pass.reads(occlusion);
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        CompiledRenderPipeline pipeline = debugView ? compiled.debugView() : compiled.apply();
        pass.executes(frame.graph().timed(APPLY_PASS, () -> {
            FullscreenPass.draw("Helion AO Apply", scene.get(), pipeline, renderPass ->
                renderPass.setUniform(AmbientOcclusionPipelines.OCCLUSION_SAMPLER, colorView(occlusion), nearest()));
            resources.finishFrame();
        }));
    }

    private static RenderTargetDescriptor target(FrameContext frame, GpuFormat format) {
        return new RenderTargetDescriptor(
            frame.scene().width(),
            frame.scene().height(),
            new RenderTargetDescriptor.TextureProperties(null, format),
            null
        );
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }

    private static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }
}
