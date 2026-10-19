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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

public final class DeferredLightingStage implements RenderStage {
    private static final String NAME = "deferred_lighting";
    private static final String SHADING_PASS = "deferred_shading";
    private static final String INDIRECT_SHARE_TARGET = "helion:indirect_share";
    private static final Vector4fc FULL_SHARE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);

    private final DeferredLightingResources resources;
    private @Nullable DeferredLightingPrograms programs;

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
        FramePass pass = frame.graph().addPass(SHADING_PASS);
        pass.reads(geometry.albedo());
        pass.reads(geometry.light());
        skyLight.ifPresent(light -> {
            pass.reads(geometry.normal());
            pass.reads(light.irradiance());
        });
        shadows.ifPresent(shadow -> pass.reads(shadow.mask()));
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        Optional<ResourceHandle<RenderTarget>> indirectShare = shadows.map(shadow -> pass.createsInternal(INDIRECT_SHARE_TARGET, new RenderTargetDescriptor(
            frame.scene().width(),
            frame.scene().height(),
            new RenderTargetDescriptor.TextureProperties(FULL_SHARE, DeferredLightingPipelines.INDIRECT_SHARE_FORMAT),
            null
        )));
        indirectShare.ifPresent(frame.shadows()::publishIndirectShare);
        boolean lightOnly = frame.settings().lighting().lightOnlyView();
        CompiledRenderPipeline pipeline = shadingPipeline(compiled, lightOnly, skyLight.isPresent(), shadows.isPresent());
        LightEnvironment environment = frame.scene().light();
        DeferredLightingSettings settings = frame.settings().lighting();
        Optional<ShadowLight> shadowLight = shadows.map(ShadowResults.Shadows::light);
        pass.executes(frame.graph().timed(SHADING_PASS, () -> {
            GpuBuffer uniforms = resources.writeUniforms(environment, settings, skyLight, shadowLight);
            List<RenderTarget> targets = indirectShare.map(share -> List.of(scene.get(), share.get())).orElseGet(() -> List.of(scene.get()));
            FullscreenPass.draw("Helion Deferred Shading", targets, pipeline, renderPass -> {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform(DeferredLightingPipelines.SETTINGS, uniforms);
                renderPass.setUniform(DeferredLightingPipelines.ALBEDO_SAMPLER, colorView(geometry.albedo()), nearest());
                renderPass.setUniform(DeferredLightingPipelines.GEOMETRY_LIGHT_SAMPLER, colorView(geometry.light()), nearest());
                skyLight.ifPresent(light -> bindSkyLight(renderPass, geometry, light));
                shadows.ifPresent(shadow -> renderPass.setUniform(DeferredLightingPipelines.SHADOW_MASK_SAMPLER, colorView(shadow.mask()), nearest()));
            });
            resources.finishFrame();
        }));
    }

    private static CompiledRenderPipeline shadingPipeline(DeferredLightingPrograms compiled, boolean lightOnly, boolean physicalSky, boolean shadows) {
        if (shadows) {
            return lightOnly ? compiled.shadowedLightOnlyView() : compiled.shadowedShading();
        }
        if (physicalSky) {
            return lightOnly ? compiled.physicalSkyLightOnlyView() : compiled.physicalSkyShading();
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
}
