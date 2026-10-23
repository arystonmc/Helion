package com.aryston.helion.render.water;

import com.aryston.helion.render.atmosphere.SceneSkyColor;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class WaterReflectionStage implements RenderStage {
    private static final String NAME = "water_reflection";
    private static final String PASS_LABEL = "Helion Water Reflection";

    private final WaterResources resources;
    private @Nullable CompiledRenderPipeline program;

    public WaterReflectionStage(WaterResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (frame.water().surface().isEmpty() || frame.water().reflections().isEmpty()) {
            return false;
        }
        program = resources.reflectionProgram(frame.atmosphere().skyColor().isPresent()).orElse(null);
        return program != null;
    }

    @Override
    public void addTo(FrameContext frame) {
        frame.water().publishReflectionPass(new Composite(
            frame,
            Objects.requireNonNull(program),
            frame.water().surface().orElseThrow(),
            frame.water().reflections().orElseThrow(),
            frame.atmosphere().skyColor()
        ));
    }

    private final class Composite implements SceneReflectionPass {
        private final FrameContext frame;
        private final CompiledRenderPipeline pipeline;
        private final ResourceHandle<RenderTarget> surface;
        private final GpuTextureView reflections;
        private final Optional<SceneSkyColor> sky;

        private Composite(
            FrameContext frame,
            CompiledRenderPipeline pipeline,
            ResourceHandle<RenderTarget> surface,
            GpuTextureView reflections,
            Optional<SceneSkyColor> sky
        ) {
            this.frame = frame;
            this.pipeline = pipeline;
            this.surface = surface;
            this.reflections = reflections;
            this.sky = sky;
        }

        @Override
        public void declareReads(FramePass pass) {
            pass.reads(surface);
            sky.ifPresent(color -> color.declareReads(pass));
        }

        @Override
        public void draw(RenderTarget scene) {
            RenderTarget water = surface.get();
            GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            FullscreenPass.draw(PASS_LABEL, scene, pipeline, renderPass -> {
                RenderSystem.bindDefaultUniforms(renderPass);
                sky.ifPresent(color -> color.bind(renderPass));
                renderPass.setUniform(WaterPipelines.REFLECTION_UNIFORM, resources.reflectionUniforms(frame.camera()));
                renderPass.setUniform(WaterPipelines.SURFACE_SAMPLER, Objects.requireNonNull(water.getColorTextureView()), nearest);
                renderPass.setUniform(WaterPipelines.DEPTH_SAMPLER, Objects.requireNonNull(water.getDepthTextureView()), nearest);
                renderPass.setUniform(WaterPipelines.REFLECTION_SAMPLER, reflections, nearest);
            });
            resources.finishReflection();
        }
    }
}
