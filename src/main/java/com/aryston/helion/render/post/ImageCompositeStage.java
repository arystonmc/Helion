package com.aryston.helion.render.post;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class ImageCompositeStage implements RenderStage {
    private static final String NAME = "composite";

    private final ImageResources resources;
    private @Nullable ImagePrograms programs;

    public ImageCompositeStage(ImageResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().image().needsComposite()) {
            return false;
        }
        Optional<ImagePrograms> compiled = resources.programs(frame);
        programs = compiled.orElse(null);
        return compiled.isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        ImagePrograms compiled = Objects.requireNonNull(programs);
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        pass.reads(scene);
        Optional<ResourceHandle<RenderTarget>> sharpened = frame.post().sharpened();
        sharpened.ifPresent(pass::reads);
        ResourceHandle<RenderTarget> color = sharpened.orElse(scene);
        Optional<ResourceHandle<RenderTarget>> bloom = frame.post().bloom();
        bloom.ifPresent(pass::reads);
        ResourceHandle<RenderTarget> output = pass.readsAndWrites(frame.targets().output());
        frame.targets().updateOutput(output);
        boolean debugView = bloom.isPresent() && frame.settings().image().bloom().debugView();
        ResourceHandle<RenderTarget> bloomSource = bloom.orElse(scene);
        ColorGrade grade = frame.settings().image().colorGrade();
        Optional<CompiledRenderPipeline> graded = grade == ColorGrade.NEUTRAL
            ? Optional.empty()
            : HelionPipelines.compiled(ImagePipelines.COMPOSITE_GRADED);
        pass.executes(frame.graph().timed(NAME, () -> {
            GpuBuffer uniforms = resources.frameUniforms(frame);
            if (debugView) {
                FullscreenPass.draw("Helion Bloom Debug", output.get(), compiled.bloomDebug(), bloomBindings(uniforms, bloomSource));
            } else {
                FullscreenPass.draw("Helion Image Composite", output.get(), graded.orElse(compiled.composite()), bloomBindings(uniforms, bloomSource)
                    .andThen(renderPass -> {
                        renderPass.setUniform(ImagePipelines.SCENE_COLOR_SAMPLER, PostSampling.colorView(color), PostSampling.nearest());
                        if (graded.isPresent()) {
                            renderPass.setUniform(
                                ImagePipelines.GRADING_LUT_SAMPLER, resources.colorGrading().lookupTable(grade), PostSampling.linear()
                            );
                        }
                    }));
            }
            output.get().copyDepthFrom(scene.get());
            resources.finishFrame();
        }));
    }

    private static Consumer<RenderPass> bloomBindings(GpuBuffer uniforms, ResourceHandle<RenderTarget> bloom) {
        return renderPass -> {
            renderPass.setUniform(ImagePipelines.SETTINGS, uniforms);
            renderPass.setUniform(ImagePipelines.BLOOM_SAMPLER, PostSampling.colorView(bloom), PostSampling.linear());
        };
    }
}
