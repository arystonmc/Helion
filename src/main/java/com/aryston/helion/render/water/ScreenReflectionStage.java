package com.aryston.helion.render.water;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import java.util.Objects;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public final class ScreenReflectionStage implements RenderStage {
    private static final String NAME = "ssr";
    private static final String PASS_LABEL = "Helion Screen Reflection";
    private static final String TARGET = "helion:screen_reflection";

    private final WaterResources resources;
    private @Nullable CompiledRenderPipeline program;

    public ScreenReflectionStage(WaterResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().reflections().screenSpace() || frame.water().surface().isEmpty()) {
            return false;
        }
        program = resources.screenReflectionProgram().orElse(null);
        return program != null;
    }

    @Override
    public void addTo(FrameContext frame) {
        CompiledRenderPipeline pipeline = Objects.requireNonNull(program);
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> surface = frame.water().surface().orElseThrow();
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        pass.reads(surface);
        pass.reads(scene);
        ResourceHandle<RenderTarget> reflections = pass.createsInternal(TARGET, new RenderTargetDescriptor(
            frame.scene().width(),
            frame.scene().height(),
            new RenderTargetDescriptor.TextureProperties(new Vector4f(), WaterPipelines.SCREEN_REFLECTION_FORMAT),
            null
        ));
        frame.water().publishScreenReflections(reflections);
        HelionCamera camera = frame.camera();
        pass.executes(frame.graph().timed(NAME, () -> {
            RenderTarget water = surface.get();
            RenderTarget lit = scene.get();
            GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            FullscreenPass.draw(PASS_LABEL, reflections.get(), pipeline, renderPass -> {
                renderPass.setUniform(WaterPipelines.SCREEN_REFLECTION_UNIFORM, resources.screenUniforms(camera));
                renderPass.setUniform(WaterPipelines.SURFACE_SAMPLER, Objects.requireNonNull(water.getColorTextureView()), nearest);
                renderPass.setUniform(WaterPipelines.DEPTH_SAMPLER, Objects.requireNonNull(water.getDepthTextureView()), nearest);
                renderPass.setUniform(WaterPipelines.SCENE_COLOR_SAMPLER, Objects.requireNonNull(lit.getColorTextureView()), linear);
                renderPass.setUniform(WaterPipelines.SCENE_DEPTH_SAMPLER, Objects.requireNonNull(lit.getDepthTextureView()), nearest);
            });
            resources.finishScreen();
        }));
    }
}
