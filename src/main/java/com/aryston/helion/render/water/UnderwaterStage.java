package com.aryston.helion.render.water;

import com.aryston.helion.render.atmosphere.SkyLight;
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
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public final class UnderwaterStage implements RenderStage {
    private static final String NAME = "underwater";
    private static final String PASS_LABEL = "Helion Underwater";
    private static final String COPY_TARGET = "helion:underwater_scene";
    private static final float DAYLIGHT_FROM = 0.0F;
    private static final float DAYLIGHT_TO = 0.25F;
    private static final float UNKNOWN_DAYLIGHT = 1.0F;
    private static final float SMOOTH_START = 3.0F;
    private static final float SMOOTH_SLOPE = 2.0F;
    private static final float SUN_BELOW_HORIZON = 0.0F;
    private static final float NO_RAIN = 0.0F;
    private static final Vector3fc DEFAULT_LIGHT = new Vector3f(0.3F, 0.9F, 0.3F).normalize();
    private static final int BASE_LEVEL = 0;
    private static final int ORIGIN = 0;

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
        if (!frame.settings().reflections().realisticWater() || !frame.water().underwater().submerged()) {
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
        ResourceHandle<RenderTarget> copy = pass.createsInternal(COPY_TARGET, new RenderTargetDescriptor(
            frame.scene().width(), frame.scene().height(), new RenderTargetDescriptor.TextureProperties(new Vector4f(), WaterPipelines.SCENE_COLOR_FORMAT), null
        ));
        UnderwaterView view = frame.water().underwater();
        float daylight = frame.atmosphere().skyLight().map(UnderwaterStage::daylight).orElse(UNKNOWN_DAYLIGHT);
        boolean moonLit = frame.atmosphere().skyLight().map(sky -> sky.sunDirection().y() < SUN_BELOW_HORIZON).orElse(false);
        Vector3fc light = frame.atmosphere().skyLight().map(sky -> moonLit ? sky.moonDirection() : sky.sunDirection()).orElse(DEFAULT_LIGHT);
        float rain = frame.water().wetness().map(Wetness::rain).orElse(NO_RAIN);
        pass.executes(frame.graph().timed(NAME, () -> {
            RenderTarget target = scene.get();
            RenderTarget copied = copy.get();
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                Objects.requireNonNull(target.getColorTexture()), Objects.requireNonNull(copied.getColorTexture()), BASE_LEVEL, ORIGIN, ORIGIN, ORIGIN, ORIGIN,
                target.width, target.height
            );
            FullscreenPass.draw(PASS_LABEL, target, pipeline, renderPass -> {
                renderPass.setUniform(WaterPipelines.UNDERWATER_UNIFORM, resources.underwaterUniforms(frame.camera(), view, daylight, light, moonLit, rain));
                renderPass.setUniform(
                    WaterPipelines.SCENE_DEPTH_SAMPLER,
                    Objects.requireNonNull(target.getDepthTextureView()),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                );
                renderPass.setUniform(
                    WaterPipelines.SCENE_COLOR_SAMPLER,
                    Objects.requireNonNull(copied.getColorTextureView()),
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
