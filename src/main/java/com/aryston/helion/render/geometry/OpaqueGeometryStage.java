package com.aryston.helion.render.geometry;

import com.aryston.helion.render.atmosphere.AtmosphereSource;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class OpaqueGeometryStage implements RenderStage {
    private static final String NAME = "opaque_geometry";
    private static final String PASS_LABEL = "Helion Opaque Geometry";
    private static final String GEOMETRY_PASS_LABEL = "Helion Geometry Buffer";
    private static final String NORMAL_TARGET = "helion:geometry_normal";
    private static final String LIGHT_TARGET = "helion:geometry_light";
    private static final String ALBEDO_TARGET = "helion:geometry_albedo";
    private static final Optional<Vector4fc> CLEAR = Optional.of(new Vector4f(0.0F, 0.0F, 0.0F, 0.0F));

    private final TerrainSource terrain;
    private final EntitySource entities;
    private final AtmosphereSource atmosphere;

    public OpaqueGeometryStage(TerrainSource terrain, EntitySource entities, AtmosphereSource atmosphere) {
        this.terrain = terrain;
        this.entities = entities;
        this.atmosphere = atmosphere;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return true;
    }

    @Override
    public void addTo(FrameContext frame) {
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        Optional<GeometryBuffer.Targets> geometry = writesGeometry(frame)
            ? Optional.of(createGeometryTargets(frame, pass))
            : Optional.empty();
        geometry.ifPresent(frame.geometry()::publish);
        pass.executes(frame.graph().timed(NAME, () -> render(scene.get(), geometry)));
    }

    private boolean writesGeometry(FrameContext frame) {
        return frame.settings().geometry().enabled()
            && frame.scene().colorFormat() == GeometryBufferPipelines.OUTPUT_FORMAT
            && terrain.supportsGeometryBuffer();
    }

    private static GeometryBuffer.Targets createGeometryTargets(FrameContext frame, FramePass pass) {
        return new GeometryBuffer.Targets(
            pass.createsInternal(NORMAL_TARGET, target(frame, GeometryBufferPipelines.NORMAL_FORMAT)),
            pass.createsInternal(LIGHT_TARGET, target(frame, GeometryBufferPipelines.LIGHT_FORMAT)),
            pass.createsInternal(ALBEDO_TARGET, target(frame, GeometryBufferPipelines.ALBEDO_FORMAT))
        );
    }

    private void render(RenderTarget target, Optional<GeometryBuffer.Targets> geometry) {
        terrain.prepareFrame();
        atmosphere.prepareTranslucents();
        entities.prepareLighting();
        if (geometry.isPresent()) {
            renderGeometry(target, geometry.get());
            return;
        }
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                    () -> PASS_LABEL,
                    colorView(target),
                    Optional.empty(),
                    target.getDepthTextureView(),
                    OptionalDouble.empty()
                )) {
            RenderSystem.bindDefaultUniforms(pass);
            terrain.renderOpaque(pass);
            entities.renderSolid(pass);
        }
    }

    private void renderGeometry(RenderTarget target, GeometryBuffer.Targets targets) {
        RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> GEOMETRY_PASS_LABEL)
            .withColorAttachment(colorView(target))
            .withColorAttachment(colorView(targets.normal().get()), CLEAR)
            .withColorAttachment(colorView(targets.light().get()), CLEAR)
            .withColorAttachment(colorView(targets.albedo().get()), CLEAR)
            .withDepthAttachment(Objects.requireNonNull(target.getDepthTextureView()))
            .build();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
            RenderSystem.bindDefaultUniforms(pass);
            terrain.renderOpaqueGeometry(pass);
        }
    }

    private static RenderTargetDescriptor target(FrameContext frame, GpuFormat format) {
        return new RenderTargetDescriptor(
            frame.scene().width(),
            frame.scene().height(),
            new RenderTargetDescriptor.TextureProperties(null, format),
            null
        );
    }

    private static GpuTextureView colorView(RenderTarget target) {
        return Objects.requireNonNull(target.getColorTextureView());
    }
}
