package com.aryston.helion.render.water;

import com.aryston.helion.render.atmosphere.SkyLight;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class UnderwaterStage implements RenderStage {
    private static final String NAME = "underwater";
    private static final String PASS_LABEL = "Helion Underwater";
    private static final float DAYLIGHT_FROM = 0.0F;
    private static final float DAYLIGHT_TO = 0.25F;
    private static final float UNKNOWN_DAYLIGHT = 0.5F;
    private static final float SMOOTH_START = 3.0F;
    private static final float SMOOTH_SLOPE = 2.0F;

    private final WaterResources resources;
    private @Nullable CompiledRenderPipeline program;

    public UnderwaterStage(WaterResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().reflections().realisticWater() || !frame.water().underwater()) {
            return false;
        }
        program = HelionPipelines.compiled(WaterPipelines.UNDERWATER).orElse(null);
        return program != null;
    }

    @Override
    public void addTo(FrameContext frame) {
        CompiledRenderPipeline pipeline = Objects.requireNonNull(program);
        FramePass pass = frame.graph().addPass(NAME);
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        float daylight = frame.atmosphere().skyLight().map(UnderwaterStage::daylight).orElse(UNKNOWN_DAYLIGHT);
        pass.executes(frame.graph().timed(NAME, () -> {
            RenderTarget target = scene.get();
            FullscreenPass.draw(PASS_LABEL, target, pipeline, renderPass -> {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform(WaterPipelines.UNDERWATER_UNIFORM, resources.underwaterUniforms(frame.camera(), daylight));
                renderPass.setUniform(
                    WaterPipelines.SCENE_DEPTH_SAMPLER,
                    Objects.requireNonNull(target.getDepthTextureView()),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                );
            });
            resources.finishUnderwater();
        }));
    }

    private static float daylight(SkyLight sky) {
        float height = sky.sunDirection().y();
        float share = Math.clamp((height - DAYLIGHT_FROM) / (DAYLIGHT_TO - DAYLIGHT_FROM), 0.0F, 1.0F);
        return share * share * (SMOOTH_START - SMOOTH_SLOPE * share) * sky.rainBrightness();
    }
}
