package com.aryston.helion.render.shadow;

import com.aryston.helion.render.atmosphere.SkyLight;
import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.GeometryBuffer;
import com.aryston.helion.render.geometry.GeometryBufferPipelines;
import com.aryston.helion.render.geometry.GlossyMask;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.blaze3d.ProjectionType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class ShadowStage implements RenderStage {
    private static final String NAME = "shadow_map";
    private static final String MASK_PASS = "shadow_mask";
    private static final String SHADOW_MAP = "helion:shadow_map_";
    private static final String MASK = "helion:shadow_mask";
    private static final String SHADOW_MAP_LABEL = "Helion Shadow Map #";
    private static final String MASK_LABEL = "Helion Shadow Mask";
    private static final String ENTITY_PASS = "shadow_entities";
    private static final String ENTITY_MAP = "helion:entity_shadow_map_";
    private static final String ENTITY_MAP_LABEL = "Helion Entity Shadow Map #";

    private final ShadowCasterSource casters;
    private final TerrainSource terrain;
    private final ShadowResources resources;
    private final GlossyMask glossy;
    private final TracedShadows traced;
    private @Nullable CompiledRenderPipeline mask;
    private @Nullable CompiledRenderPipeline tracedMask;
    private @Nullable ShadowLight light;

    public ShadowStage(ShadowCasterSource casters, TerrainSource terrain, ShadowResources resources, GlossyMask glossy, TracedShadows traced) {
        this.casters = casters;
        this.terrain = terrain;
        this.resources = resources;
        this.glossy = glossy;
        this.traced = traced;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return resources.markDrawn(decideActive(frame));
    }

    private boolean decideActive(FrameContext frame) {
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
        tracedMask = HelionPipelines.compiled(ShadowPipelines.MASK_TRACED).orElse(null);
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
        List<RenderTarget> shadowMaps = resources.shadowMaps(resolution);
        long now = System.nanoTime();
        List<ShadowCascadeCache.Slot> due = resources.cache().update(cascades, camera.position(), shadowLight, resolution, now);
        casters.prepare(due.stream().map(slot -> cascades.get(slot.cascade())).toList());
        List<ResourceHandle<RenderTarget>> imported = new ArrayList<>(shadowMaps.size());
        for (int target = 0; target < shadowMaps.size(); target++) {
            imported.add(frame.graph().builder().importExternal(SHADOW_MAP + target, shadowMaps.get(target)));
        }
        List<ResourceHandle<RenderTarget>> maps = addShadowMapPass(frame, imported, cascades, due);
        List<ResourceHandle<RenderTarget>> entityMaps = addEntityPass(frame, resolution);
        float previousWeight = resources.cache().previousWeight(now);
        Optional<GpuTextureView> tracedShadows = tracedMask == null ? Optional.empty() : traced.addTo(frame, shadowLight);
        ResourceHandle<RenderTarget> visibility = addMaskPass(frame, maps, entityMaps, distance, resolution, previousWeight, tracedShadows);
        frame.shadows().publish(visibility, shadowLight);
    }

    private List<ResourceHandle<RenderTarget>> addShadowMapPass(
        FrameContext frame,
        List<ResourceHandle<RenderTarget>> imported,
        List<ShadowCascade> cascades,
        List<ShadowCascadeCache.Slot> due
    ) {
        FramePass pass = frame.graph().addPass(NAME);
        List<ResourceHandle<RenderTarget>> maps = new ArrayList<>(imported);
        due.forEach(slot -> maps.set(slot.target(), pass.readsAndWrites(imported.get(slot.target()))));
        pass.executes(frame.graph().timed(NAME, () -> {
            GpuTextureView materials = glossy.update(terrain.glossyCells());
            for (int order = 0; order < due.size(); order++) {
                ShadowCascadeCache.Slot slot = due.get(order);
                RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> SHADOW_MAP_LABEL + slot.target())
                    .withDepthAttachment(depthView(maps.get(slot.target())), OptionalDouble.of(ShadowPipelines.CLEAR_DEPTH))
                    .build();
                try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
                    RenderSystem.bindDefaultUniforms(renderPass);
                    renderPass.setUniform(
                        ShadowPipelines.PROJECTION, resources.writeProjection(slot.cascade(), cascades.get(slot.cascade()).projection())
                    );
                    renderPass.setUniform(GeometryBufferPipelines.GLOSSY_SAMPLER, materials, nearest());
                    casters.renderCascade(order, renderPass);
                }
            }
        }));
        return List.copyOf(maps);
    }

    private List<ResourceHandle<RenderTarget>> addEntityPass(FrameContext frame, int resolution) {
        List<RenderTarget> targets = resources.entityMaps(resolution);
        FramePass pass = frame.graph().addPass(ENTITY_PASS);
        List<ResourceHandle<RenderTarget>> maps = new ArrayList<>(ShadowPipelines.ENTITY_CASCADES);
        for (int index = 0; index < ShadowPipelines.ENTITY_CASCADES; index++) {
            maps.add(pass.readsAndWrites(frame.graph().builder().importExternal(ENTITY_MAP + index, targets.get(index))));
        }
        HelionCamera camera = frame.camera();
        pass.executes(frame.graph().timed(ENTITY_PASS, () -> {
            for (int index = 0; index < ShadowPipelines.ENTITY_CASCADES; index++) {
                Matrix4f projection = ShadowCascades.entityProjection(
                    resources.cache().maskMatrix(resources.cache().currentSet(), index, camera.position()),
                    camera.viewRotation(),
                    camera.zeroToOneDepth()
                );
                renderEntities(index, maps.get(index).get(), resources.writeEntityProjection(index, projection));
            }
        }));
        return List.copyOf(maps);
    }

    private void renderEntities(int index, RenderTarget target, GpuBuffer projection) {
        RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> ENTITY_MAP_LABEL + index)
            .withColorAttachment(Objects.requireNonNull(target.getColorTextureView()))
            .withDepthAttachment(Objects.requireNonNull(target.getDepthTextureView()), OptionalDouble.of(ShadowPipelines.ENTITY_CLEAR_DEPTH))
            .build();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection.slice(), ProjectionType.ORTHOGRAPHIC);
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
            casters.renderEntities(renderPass);
        } finally {
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private ResourceHandle<RenderTarget> addMaskPass(
        FrameContext frame,
        List<ResourceHandle<RenderTarget>> shadowMaps,
        List<ResourceHandle<RenderTarget>> entityMaps,
        float distance,
        int resolution,
        float previousWeight,
        Optional<GpuTextureView> tracedShadows
    ) {
        GeometryBuffer.Targets geometry = frame.geometry().targets().orElseThrow();
        CompiledRenderPipeline pipeline = Objects.requireNonNull(tracedShadows.isPresent() ? tracedMask : mask);
        FramePass pass = frame.graph().addPass(MASK_PASS);
        shadowMaps.forEach(pass::reads);
        entityMaps.forEach(pass::reads);
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
            GpuBuffer settings = resources.writeSettings(camera, distance, resolution, previousWeight);
            int current = resources.cache().currentSet();
            int previous = previousWeight > 0.0F ? resources.cache().previousSet() : current;
            FullscreenPass.draw(MASK_LABEL, visibility.get(), pipeline, renderPass -> {
                renderPass.setUniform(ShadowPipelines.SETTINGS, settings);
                renderPass.setUniform(ShadowPipelines.DEPTH_SAMPLER, depthView(scene), nearest());
                renderPass.setUniform(ShadowPipelines.GEOMETRY_NORMAL_SAMPLER, colorView(geometry.normal()), nearest());
                tracedShadows.ifPresent(view -> renderPass.setUniform(
                    ShadowPipelines.TRACED_SHADOW_SAMPLER, view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                ));
                for (int index = 0; index < ShadowCascades.COUNT; index++) {
                    renderPass.setUniform(
                        ShadowPipelines.SHADOW_MAP_SAMPLERS.get(index), depthView(shadowMaps.get(current * ShadowCascades.COUNT + index)), nearest()
                    );
                    renderPass.setUniform(
                        ShadowPipelines.PREVIOUS_SHADOW_MAP_SAMPLERS.get(index),
                        depthView(shadowMaps.get(previous * ShadowCascades.COUNT + index)),
                        nearest()
                    );
                }
                for (int index = 0; index < ShadowPipelines.ENTITY_CASCADES; index++) {
                    renderPass.setUniform(ShadowPipelines.ENTITY_SHADOW_SAMPLERS.get(index), depthView(entityMaps.get(index)), nearest());
                }
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
