package com.aryston.helion.render.water;

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
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.Objects;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public final class HeatShimmerStage implements RenderStage {
    private static final String NAME = "heat_shimmer";
    private static final String PASS_LABEL = "Helion Heat Shimmer";
    private static final String SOURCE_TARGET = "helion:heat_source";
    private static final int BASE_LEVEL = 0;
    private static final int ORIGIN = 0;

    private final WaterResources resources;
    private @Nullable CompiledRenderPipeline program;

    public HeatShimmerStage(WaterResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().reflections().realisticWater() || !frame.water().lavaNearby() || frame.water().underwater()
            || frame.geometry().targets().isEmpty() || frame.scene().colorFormat() != WaterPipelines.SCENE_COLOR_FORMAT) {
            return false;
        }
        program = HelionPipelines.compiled(WaterPipelines.HEAT_SHIMMER).orElse(null);
        return program != null;
    }

    @Override
    public void addTo(FrameContext frame) {
        CompiledRenderPipeline pipeline = Objects.requireNonNull(program);
        GeometryBuffer.Targets geometry = frame.geometry().targets().orElseThrow();
        FramePass pass = frame.graph().addPass(NAME);
        pass.reads(geometry.light());
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        ResourceHandle<RenderTarget> source = pass.createsInternal(SOURCE_TARGET, new RenderTargetDescriptor(
            frame.scene().width(),
            frame.scene().height(),
            new RenderTargetDescriptor.TextureProperties(new Vector4f(), WaterPipelines.SCENE_COLOR_FORMAT),
            null
        ));
        pass.executes(frame.graph().timed(NAME, () -> {
            RenderTarget target = scene.get();
            RenderTarget copy = source.get();
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                Objects.requireNonNull(target.getColorTexture()), Objects.requireNonNull(copy.getColorTexture()), BASE_LEVEL, ORIGIN, ORIGIN, ORIGIN, ORIGIN,
                target.width, target.height
            );
            FullscreenPass.draw(PASS_LABEL, target, pipeline, renderPass -> {
                renderPass.setUniform(WaterPipelines.HEAT_UNIFORM, resources.heatUniforms());
                renderPass.setUniform(
                    WaterPipelines.HEAT_SOURCE_SAMPLER,
                    Objects.requireNonNull(copy.getColorTextureView()),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                );
                renderPass.setUniform(
                    WaterPipelines.GEOMETRY_LIGHT_SAMPLER,
                    Objects.requireNonNull(geometry.light().get().getColorTextureView()),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                );
            });
            resources.finishHeat();
        }));
    }
}
