package com.aryston.helion.render.lighting;

import com.aryston.helion.render.atmosphere.SkyLight;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.aryston.helion.render.shadow.ShadowLight;
import com.aryston.helion.render.shadow.ShadowResults;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
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
        Optional<SkyLight> skyLight = frame.atmosphere().skyLight();
        Optional<ShadowResults.Shadows> shadows = skyLight.isPresent() ? frame.shadows().shadows() : Optional.empty();
        ResourceHandle<RenderTarget> lightBuffer = addLightPass(frame, compiled, geometry, skyLight, shadows);
        addShadingPass(frame, compiled, geometry, lightBuffer, skyLight.flatMap(light -> shadows.map(shadow -> new ShadowInputs(light, shadow))));
    }

    private ResourceHandle<RenderTarget> addLightPass(
        FrameContext frame,
        DeferredLightingPrograms compiled,
        GeometryBuffer.Targets geometry,
        Optional<SkyLight> skyLight,
        Optional<ShadowResults.Shadows> shadows
    ) {
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(geometry.light());
        skyLight.ifPresent(light -> {
            pass.reads(geometry.normal());
            pass.reads(light.irradiance());
        });
        CompiledRenderPipeline pipeline = skyLight.isPresent() ? compiled.physicalSkyLight() : compiled.light();
        Optional<ShadowLight> shadowLight = shadows.map(ShadowResults.Shadows::light);
        ResourceHandle<RenderTarget> lightBuffer = pass.createsInternal(LIGHT_BUFFER, new RenderTargetDescriptor(
            frame.scene().width(),
            frame.scene().height(),
            new RenderTargetDescriptor.TextureProperties(null, DeferredLightingPipelines.LIGHT_BUFFER_FORMAT),
            null
        ));
        LightEnvironment environment = frame.scene().light();
        DeferredLightingSettings settings = frame.settings().lighting();
        pass.executes(frame.graph().timed(NAME, () -> {
            frameUniforms = resources.writeUniforms(environment, settings, skyLight, shadowLight);
            FullscreenPass.draw("Helion Deferred Light", lightBuffer.get(), pipeline, renderPass -> {
                renderPass.setUniform(DeferredLightingPipelines.SETTINGS, Objects.requireNonNull(frameUniforms));
                renderPass.setUniform(DeferredLightingPipelines.GEOMETRY_LIGHT_SAMPLER, colorView(geometry.light()), nearest());
                skyLight.ifPresent(light -> bindSkyLight(renderPass, geometry, light));
            });
        }));
        return lightBuffer;
    }

    private void addShadingPass(
        FrameContext frame,
        DeferredLightingPrograms compiled,
        GeometryBuffer.Targets geometry,
        ResourceHandle<RenderTarget> lightBuffer,
        Optional<ShadowInputs> shadows
    ) {
        FramePass pass = frame.graph().addPass(SHADING_PASS);
        pass.reads(lightBuffer);
        pass.reads(geometry.albedo());
        pass.reads(geometry.light());
        shadows.ifPresent(shadow -> {
            pass.reads(geometry.normal());
            pass.reads(shadow.skyLight().irradiance());
            pass.reads(shadow.results().mask());
        });
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        CompiledRenderPipeline pipeline = shadingPipeline(compiled, frame.settings().lighting().lightOnlyView(), shadows.isPresent());
        pass.executes(frame.graph().timed(SHADING_PASS, () -> {
            FullscreenPass.draw("Helion Deferred Shading", scene.get(), pipeline, renderPass -> {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform(DeferredLightingPipelines.SETTINGS, Objects.requireNonNull(frameUniforms));
                renderPass.setUniform(DeferredLightingPipelines.LIGHT_BUFFER_SAMPLER, colorView(lightBuffer), nearest());
                renderPass.setUniform(DeferredLightingPipelines.ALBEDO_SAMPLER, colorView(geometry.albedo()), nearest());
                renderPass.setUniform(DeferredLightingPipelines.GEOMETRY_LIGHT_SAMPLER, colorView(geometry.light()), nearest());
                shadows.ifPresent(shadow -> {
                    bindSkyLight(renderPass, geometry, shadow.skyLight());
                    renderPass.setUniform(DeferredLightingPipelines.SHADOW_MASK_SAMPLER, colorView(shadow.results().mask()), nearest());
                });
            });
            resources.finishFrame();
        }));
    }

    private static CompiledRenderPipeline shadingPipeline(DeferredLightingPrograms compiled, boolean lightOnly, boolean shadows) {
        if (shadows) {
            return lightOnly ? compiled.shadowedLightOnlyView() : compiled.shadowedShading();
        }
        return lightOnly ? compiled.lightOnlyView() : compiled.shading();
    }

    private static void bindSkyLight(RenderPass renderPass, GeometryBuffer.Targets geometry, SkyLight light) {
        renderPass.setUniform(DeferredLightingPipelines.GEOMETRY_NORMAL_SAMPLER, colorView(geometry.normal()), nearest());
        renderPass.setUniform(DeferredLightingPipelines.SKY_LIGHT_SAMPLER, colorView(light.irradiance()), nearest());
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }

    private static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    private record ShadowInputs(SkyLight skyLight, ShadowResults.Shadows results) {
    }
}
