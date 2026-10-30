package com.aryston.helion.render.water;

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
import java.util.Objects;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public final class GlassMistStage implements RenderStage {
    private static final String NAME = "glass_mist";
    private static final String PASS_LABEL = "Helion Glass Mist";
    private static final String TARGET = "helion:glass_mist";
    private static final int SCALE = 2;
    private static final int MIN_SIZE = 1;

    private final WaterResources resources;
    private @Nullable CompiledRenderPipeline program;

    public GlassMistStage(WaterResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (frame.water().refraction().isEmpty() || frame.water().glassSurface().isEmpty() || frame.water().wetness().orElse(Wetness.DRY).rain() <= 0.0F) {
            return false;
        }
        program = resources.glassMistProgram().orElse(null);
        return program != null;
    }

    @Override
    public void addTo(FrameContext frame) {
        CompiledRenderPipeline pipeline = Objects.requireNonNull(program);
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> refraction = frame.water().refraction().orElseThrow();
        ResourceHandle<RenderTarget> glass = frame.water().glassSurface().orElseThrow();
        pass.reads(refraction);
        pass.reads(glass);
        ResourceHandle<RenderTarget> mist = pass.createsInternal(TARGET, descriptor(frame));
        frame.water().publishGlassMist(mist);
        pass.executes(frame.graph().timed(NAME, () -> FullscreenPass.draw(PASS_LABEL, mist.get(), pipeline, renderPass -> {
            renderPass.setUniform(WaterPipelines.REFRACTION_SAMPLER, Objects.requireNonNull(refraction.get().getColorTextureView()), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.setUniform(WaterPipelines.GLASS_SURFACE_SAMPLER, Objects.requireNonNull(glass.get().getColorTextureView()), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        })));
    }

    private static RenderTargetDescriptor descriptor(FrameContext frame) {
        return new RenderTargetDescriptor(
            Math.max(frame.scene().width() / SCALE, MIN_SIZE),
            Math.max(frame.scene().height() / SCALE, MIN_SIZE),
            new RenderTargetDescriptor.TextureProperties(new Vector4f(), WaterPipelines.SCREEN_REFLECTION_FORMAT),
            null
        );
    }
}
