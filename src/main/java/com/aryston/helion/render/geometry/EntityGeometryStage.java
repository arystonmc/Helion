package com.aryston.helion.render.geometry;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;

public final class EntityGeometryStage implements RenderStage {
    private static final String NAME = "entity_geometry";
    private static final String PASS_LABEL = "Helion Entity Geometry";

    private final EntitySource entities;

    public EntityGeometryStage(EntitySource entities) {
        this.entities = entities;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        return frame.geometry().targets().isPresent() && entities.supportsGeometryBuffer();
    }

    @Override
    public void addTo(FrameContext frame) {
        GeometryBuffer.Targets written = frame.geometry().targets().orElseThrow();
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        GeometryBuffer.Targets geometry = new GeometryBuffer.Targets(
            pass.readsAndWrites(written.normal()),
            pass.readsAndWrites(written.light()),
            pass.readsAndWrites(written.albedo())
        );
        frame.geometry().publish(geometry);
        frame.geometry().publishEntities();
        pass.executes(frame.graph().timed(NAME, () -> render(scene.get(), geometry)));
    }

    private void render(RenderTarget target, GeometryBuffer.Targets geometry) {
        RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> PASS_LABEL)
            .withColorAttachment(colorView(target))
            .withColorAttachment(colorView(geometry.normal().get()))
            .withColorAttachment(colorView(geometry.light().get()))
            .withColorAttachment(colorView(geometry.albedo().get()))
            .withDepthAttachment(Objects.requireNonNull(target.getDepthTextureView()))
            .build();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
            RenderSystem.bindDefaultUniforms(pass);
            entities.renderSolidGeometry(pass);
        }
    }

    private static GpuTextureView colorView(RenderTarget target) {
        return Objects.requireNonNull(target.getColorTextureView());
    }
}
