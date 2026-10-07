package com.aryston.helion.render.shadow;

import com.aryston.helion.render.atmosphere.SkyLight;
import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public final class ShadowStage implements RenderStage {
    private static final String NAME = "shadow_map";
    private static final String MASK_PASS = "shadow_mask";
    private static final String SHADOW_MAP = "helion:shadow_map";
    private static final String MASK = "helion:shadow_mask";
    private static final String SHADOW_MAP_LABEL = "Helion Shadow Map";
    private static final String MASK_LABEL = "Helion Shadow Mask";
    private static final int FIRST_ROW = 0;

    private final ShadowCasterSource casters;
    private final ShadowResources resources;
    private @Nullable CompiledRenderPipeline mask;
    private @Nullable ShadowLight light;

    public ShadowStage(ShadowCasterSource casters, ShadowResources resources) {
        this.casters = casters;
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        Optional<SkyLight> skyLight = frame.atmosphere().skyLight();
        if (!frame.settings().shadows().enabled()
            || !frame.settings().lighting().enabled()
            || frame.geometry().targets().isEmpty()
            || skyLight.isEmpty()
            || frame.scene().colorFormat() != ShadowPipelines.SCENE_COLOR_FORMAT
            || !casters.supportsShadows()) {
            return false;
        }
        light = ShadowLight.choose(skyLight.get().sunDirection(), skyLight.get().moonDirection()).orElse(null);
        if (light == null) {
            return false;
        }
        mask = HelionPipelines.compiled(ShadowPipelines.MASK).orElse(null);
        if (mask == null) {
            resources.reportMissingPipelines();
            return false;
        }
        return true;
    }

    @Override
    public void addTo(FrameContext frame) {
        ShadowLight shadowLight = Objects.requireNonNull(light);
        HelionCamera camera = frame.camera();
        int resolution = frame.settings().shadows().quality().cascadeResolution();
        float distance = ShadowCascades.distance(casters.renderDistanceBlocks());
        List<ShadowCascade> cascades = ShadowCascades.compute(
            camera.position(), camera.viewRotation(), camera.projection(), shadowLight.direction(), distance, resolution, camera.zeroToOneDepth()
        );
        casters.prepare(cascades);
        ResourceHandle<RenderTarget> shadowMap = addShadowMapPass(frame, cascades, resolution);
        ResourceHandle<RenderTarget> visibility = addMaskPass(frame, shadowMap, cascades, shadowLight, distance, resolution);
        frame.shadows().publish(visibility, shadowLight);
    }

    private ResourceHandle<RenderTarget> addShadowMapPass(FrameContext frame, List<ShadowCascade> cascades, int resolution) {
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> shadowMap = pass.readsAndWrites(
            frame.graph().builder().importExternal(SHADOW_MAP, resources.shadowMap(resolution))
        );
        pass.executes(frame.graph().timed(NAME, () -> {
            RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> SHADOW_MAP_LABEL)
                .withDepthAttachment(depthView(shadowMap), OptionalDouble.of(ShadowPipelines.CLEAR_DEPTH))
                .build();
            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
                RenderSystem.bindDefaultUniforms(renderPass);
                for (int index = 0; index < cascades.size(); index++) {
                    renderPass.enableScissor(index * resolution, FIRST_ROW, resolution, resolution);
                    Matrix4fc atlasProjection = ShadowCascades.atlasProjection(index, cascades.get(index).projection());
                    renderPass.setUniform(ShadowPipelines.PROJECTION, resources.writeProjection(index, atlasProjection));
                    casters.renderCascade(index, renderPass);
                }
                renderPass.disableScissor();
            }
        }));
        return shadowMap;
    }

    private ResourceHandle<RenderTarget> addMaskPass(
        FrameContext frame,
        ResourceHandle<RenderTarget> shadowMap,
        List<ShadowCascade> cascades,
        ShadowLight shadowLight,
        float distance,
        int resolution
    ) {
        GeometryBuffer.Targets geometry = frame.geometry().targets().orElseThrow();
        CompiledRenderPipeline pipeline = Objects.requireNonNull(mask);
        FramePass pass = frame.graph().addPass(MASK_PASS);
        pass.reads(shadowMap);
        pass.reads(geometry.normal());
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        pass.reads(scene);
        ResourceHandle<RenderTarget> visibility = pass.createsInternal(MASK, new RenderTargetDescriptor(
            frame.scene().width(),
            frame.scene().height(),
            new RenderTargetDescriptor.TextureProperties(null, ShadowPipelines.MASK_FORMAT),
            null
        ));
        HelionCamera camera = frame.camera();
        pass.executes(frame.graph().timed(MASK_PASS, () -> {
            GpuBuffer settings = resources.writeSettings(camera, cascades, shadowLight, distance, resolution);
            FullscreenPass.draw(MASK_LABEL, visibility.get(), pipeline, renderPass -> {
                renderPass.setUniform(ShadowPipelines.SETTINGS, settings);
                renderPass.setUniform(ShadowPipelines.DEPTH_SAMPLER, depthView(scene), nearest());
                renderPass.setUniform(ShadowPipelines.GEOMETRY_NORMAL_SAMPLER, colorView(geometry.normal()), nearest());
                renderPass.setUniform(ShadowPipelines.SHADOW_MAP_SAMPLER, depthView(shadowMap), nearest());
            });
            resources.finishFrame();
        }));
        return visibility;
    }

    private static GpuTextureView depthView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getDepthTextureView());
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }

    private static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }
}
