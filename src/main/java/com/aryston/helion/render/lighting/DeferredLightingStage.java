package com.aryston.helion.render.lighting;

import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class DeferredLightingStage implements RenderStage {
    private static final String NAME = "deferred_lighting";
    private static final String SHADING_PASS = "deferred_shading";
    private static final String LIGHT_BUFFER = "helion:light_buffer";

    private final DeferredLightingResources resources;
    private @Nullable DeferredLightingPrograms programs;
    private @Nullable GpuBuffer frameUniforms;

    public DeferredLightingStage(DeferredLightingResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().lighting().enabled()
            || frame.geometry().targets().isEmpty()
            || frame.scene().colorFormat() != DeferredLightingPipelines.SCENE_COLOR_FORMAT) {
            return false;
        }
        Optional<DeferredLightingPrograms> compiled = DeferredLightingPrograms.compile();
        if (compiled.isEmpty()) {
            resources.reportMissingPipelines();
            return false;
        }
        programs = compiled.get();
        return true;
    }

    @Override
    public void addTo(FrameContext frame) {
        DeferredLightingPrograms compiled = Objects.requireNonNull(programs);
        GeometryBuffer.Targets geometry = frame.geometry().targets().orElseThrow();
        ResourceHandle<RenderTarget> lightBuffer = addLightPass(frame, compiled, geometry);
        addShadingPass(frame, compiled, geometry, lightBuffer);
    }

    private ResourceHandle<RenderTarget> addLightPass(FrameContext frame, DeferredLightingPrograms compiled, GeometryBuffer.Targets geometry) {
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(geometry.light());
        ResourceHandle<RenderTarget> lightBuffer = pass.createsInternal(LIGHT_BUFFER, new RenderTargetDescriptor(
            frame.scene().width(),
            frame.scene().height(),
            new RenderTargetDescriptor.TextureProperties(null, DeferredLightingPipelines.LIGHT_BUFFER_FORMAT),
            null
        ));
        LightEnvironment environment = frame.scene().light();
        DeferredLightingSettings settings = frame.settings().lighting();
        pass.executes(frame.graph().timed(NAME, () -> {
            frameUniforms = resources.writeUniforms(environment, settings);
            FullscreenPass.draw("Helion Deferred Light", lightBuffer.get(), compiled.light(), renderPass -> {
                renderPass.setUniform(DeferredLightingPipelines.SETTINGS, Objects.requireNonNull(frameUniforms));
                renderPass.setUniform(DeferredLightingPipelines.GEOMETRY_LIGHT_SAMPLER, colorView(geometry.light()), nearest());
            });
        }));
        return lightBuffer;
    }

    private void addShadingPass(
        FrameContext frame,
        DeferredLightingPrograms compiled,
        GeometryBuffer.Targets geometry,
        ResourceHandle<RenderTarget> lightBuffer
    ) {
        FramePass pass = frame.graph().addPass(SHADING_PASS);
        pass.reads(lightBuffer);
        pass.reads(geometry.albedo());
        pass.reads(geometry.light());
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        CompiledRenderPipeline pipeline = frame.settings().lighting().lightOnlyView() ? compiled.lightOnlyView() : compiled.shading();
        pass.executes(frame.graph().timed(SHADING_PASS, () -> {
            FullscreenPass.draw("Helion Deferred Shading", scene.get(), pipeline, renderPass -> {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform(DeferredLightingPipelines.SETTINGS, Objects.requireNonNull(frameUniforms));
                renderPass.setUniform(DeferredLightingPipelines.LIGHT_BUFFER_SAMPLER, colorView(lightBuffer), nearest());
                renderPass.setUniform(DeferredLightingPipelines.ALBEDO_SAMPLER, colorView(geometry.albedo()), nearest());
                renderPass.setUniform(DeferredLightingPipelines.GEOMETRY_LIGHT_SAMPLER, colorView(geometry.light()), nearest());
            });
            resources.finishFrame();
        }));
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }

    private static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }
}
