package com.aryston.helion.render.water;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
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
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public final class ScreenReflectionStage implements RenderStage {
    private static final String NAME = "ssr";
    private static final String COMPOSITE_NAME = "ssr_polished";
    private static final String PASS_LABEL = "Helion Screen Reflection";
    private static final String COMPOSITE_LABEL = "Helion Polished Reflection";
    private static final String TARGET = "helion:screen_reflection";
    private static final String POLISHED_TARGET = "helion:polished_reflection";

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
        Optional<GeometryBuffer.Targets> geometry = frame.geometry().targets();
        pass.reads(surface);
        pass.reads(scene);
        geometry.ifPresent(targets -> {
            pass.reads(targets.normal());
            pass.reads(targets.light());
        });
        ResourceHandle<RenderTarget> reflections = pass.createsInternal(TARGET, descriptor(frame));
        ResourceHandle<RenderTarget> polished = pass.createsInternal(POLISHED_TARGET, descriptor(frame));
        frame.water().publishScreenReflections(reflections);
        HelionCamera camera = frame.camera();
        pass.executes(frame.graph().timed(NAME, () -> {
            RenderTarget water = surface.get();
            RenderTarget lit = scene.get();
            GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuTextureView standIn = Objects.requireNonNull(water.getColorTextureView());
            FullscreenPass.draw(PASS_LABEL, List.of(reflections.get(), polished.get()), pipeline, renderPass -> {
                renderPass.setUniform(WaterPipelines.SCREEN_REFLECTION_UNIFORM, resources.screenUniforms(camera, geometry.isPresent()));
                renderPass.setUniform(WaterPipelines.SURFACE_SAMPLER, standIn, nearest);
                renderPass.setUniform(WaterPipelines.DEPTH_SAMPLER, Objects.requireNonNull(water.getDepthTextureView()), nearest);
                renderPass.setUniform(WaterPipelines.SCENE_COLOR_SAMPLER, Objects.requireNonNull(lit.getColorTextureView()), linear);
                renderPass.setUniform(WaterPipelines.SCENE_DEPTH_SAMPLER, Objects.requireNonNull(lit.getDepthTextureView()), nearest);
                renderPass.setUniform(WaterPipelines.GEOMETRY_NORMAL_SAMPLER, geometry.map(targets -> colorView(targets.normal())).orElse(standIn), nearest);
                renderPass.setUniform(WaterPipelines.GEOMETRY_LIGHT_SAMPLER, geometry.map(targets -> colorView(targets.light())).orElse(standIn), nearest);
            });
            resources.finishScreen();
        }));
        if (geometry.isPresent()) {
            resources.polishedProgram().ifPresent(composite -> addComposite(frame, polished, composite));
        }
    }

    private static void addComposite(FrameContext frame, ResourceHandle<RenderTarget> polished, CompiledRenderPipeline composite) {
        FramePass pass = frame.graph().addPass(COMPOSITE_NAME);
        pass.reads(polished);
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        pass.executes(frame.graph().timed(COMPOSITE_NAME, () -> FullscreenPass.draw(COMPOSITE_LABEL, scene.get(), composite, renderPass ->
            renderPass.setUniform(WaterPipelines.POLISHED_SAMPLER, colorView(polished), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)))));
    }

    private static RenderTargetDescriptor descriptor(FrameContext frame) {
        return new RenderTargetDescriptor(
            frame.scene().width(),
            frame.scene().height(),
            new RenderTargetDescriptor.TextureProperties(new Vector4f(), WaterPipelines.SCREEN_REFLECTION_FORMAT),
            null
        );
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }
}
