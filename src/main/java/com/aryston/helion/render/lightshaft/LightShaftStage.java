package com.aryston.helion.render.lightshaft;

import com.aryston.helion.render.atmosphere.SkyLight;
import com.aryston.helion.render.clouds.SceneCloudPass;
import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class LightShaftStage implements RenderStage {
    private static final String NAME = "light_shafts";
    private static final String MASK_PASS = "light_shaft_mask";
    private static final String BLUR_PASS = "light_shaft_blur";
    private static final String TARGET_PREFIX = "helion:";

    private final LightShaftResources resources;
    private @Nullable LightShaftPrograms programs;
    private @Nullable SkyLight skyLight;
    private @Nullable LightShaftView view;
    private @Nullable GpuBuffer frameUniforms;

    public LightShaftStage(LightShaftResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().sky().lightShafts() || frame.scene().colorFormat() != LightShaftPipelines.SCENE_COLOR_FORMAT) {
            return false;
        }
        skyLight = frame.atmosphere().skyLight().orElse(null);
        if (skyLight == null) {
            return false;
        }
        view = LightShaftView.of(
            frame.camera().levelViewProjection(), skyLight.sunDirection(), skyLight.moonDirection(), skyLight.rainBrightness()
        ).orElse(null);
        if (view == null) {
            return false;
        }
        programs = resources.programs().orElse(null);
        return programs != null;
    }

    @Override
    public void addTo(FrameContext frame) {
        LightShaftPrograms compiled = Objects.requireNonNull(programs);
        int width = Math.max(1, frame.scene().width() / LightShaftResources.RESOLUTION_DIVISOR);
        int height = Math.max(1, frame.scene().height() / LightShaftResources.RESOLUTION_DIVISOR);
        Uniforms uniforms = new Uniforms(frame, width, height);
        ResourceHandle<RenderTarget> mask = addMaskPass(frame, compiled, uniforms, width, height);
        ResourceHandle<RenderTarget> shafts = addBlurPass(frame, compiled, uniforms, mask, width, height);
        frame.atmosphere().publishLightShafts(new Composite(compiled, uniforms, shafts, Objects.requireNonNull(skyLight)));
    }

    private ResourceHandle<RenderTarget> addMaskPass(FrameContext frame, LightShaftPrograms compiled, Uniforms uniforms, int width, int height) {
        FramePass pass = frame.graph().addPass(MASK_PASS);
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        pass.reads(scene);
        Optional<ResourceHandle<RenderTarget>> clouds = frame.atmosphere().clouds().map(SceneCloudPass::resolved);
        clouds.ifPresent(pass::reads);
        ResourceHandle<RenderTarget> mask = pass.createsInternal(TARGET_PREFIX + MASK_PASS, target(width, height, LightShaftPipelines.MASK_FORMAT));
        pass.executes(frame.graph().timed(MASK_PASS, () -> FullscreenPass.draw(
            "Helion Light Shaft Mask", mask.get(), clouds.isPresent() ? compiled.cloudMask() : compiled.mask(), renderPass -> {
                renderPass.setUniform(LightShaftPipelines.SETTINGS, uniforms.get());
                renderPass.setUniform(LightShaftPipelines.DEPTH_SAMPLER, Objects.requireNonNull(scene.get().getDepthTextureView()), sampler(FilterMode.NEAREST));
                clouds.ifPresent(cloud -> renderPass.setUniform(LightShaftPipelines.CLOUD_SAMPLER, colorView(cloud), sampler(FilterMode.LINEAR)));
            })));
        return mask;
    }

    private ResourceHandle<RenderTarget> addBlurPass(
        FrameContext frame,
        LightShaftPrograms compiled,
        Uniforms uniforms,
        ResourceHandle<RenderTarget> mask,
        int width,
        int height
    ) {
        FramePass pass = frame.graph().addPass(BLUR_PASS);
        pass.reads(mask);
        ResourceHandle<RenderTarget> shafts = pass.createsInternal(TARGET_PREFIX + BLUR_PASS, target(width, height, LightShaftPipelines.SHAFT_FORMAT));
        pass.executes(frame.graph().timed(BLUR_PASS, () -> FullscreenPass.draw(
            "Helion Light Shaft Blur", shafts.get(), compiled.blur(), renderPass -> {
                renderPass.setUniform(LightShaftPipelines.SETTINGS, uniforms.get());
                renderPass.setUniform(LightShaftPipelines.MASK_SAMPLER, colorView(mask), sampler(FilterMode.LINEAR));
            })));
        return shafts;
    }

    private static RenderTargetDescriptor target(int width, int height, GpuFormat format) {
        return new RenderTargetDescriptor(width, height, new RenderTargetDescriptor.TextureProperties(null, format), null);
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }

    private static GpuSampler sampler(FilterMode filter) {
        return RenderSystem.getSamplerCache().getClampToEdge(filter);
    }

    private final class Uniforms {
        private final FrameContext frame;
        private final LightShaftView light;
        private final int width;
        private final int height;

        private Uniforms(FrameContext frame, int width, int height) {
            this.frame = frame;
            this.light = Objects.requireNonNull(view);
            this.width = width;
            this.height = height;
        }

        GpuBuffer get() {
            if (frameUniforms == null) {
                frameUniforms = resources.writeUniforms(frame.camera(), light, frame.scene().width(), frame.scene().height(), width, height);
            }
            return frameUniforms;
        }
    }

    private final class Composite implements SceneLightShaftPass {
        private final LightShaftPrograms compiled;
        private final Uniforms uniforms;
        private final ResourceHandle<RenderTarget> shafts;
        private final SkyLight light;

        private Composite(LightShaftPrograms compiled, Uniforms uniforms, ResourceHandle<RenderTarget> shafts, SkyLight light) {
            this.compiled = compiled;
            this.uniforms = uniforms;
            this.shafts = shafts;
            this.light = light;
        }

        @Override
        public void declareReads(FramePass pass) {
            pass.reads(shafts);
            pass.reads(light.irradiance());
        }

        @Override
        public void draw(RenderTarget scene) {
            FullscreenPass.draw("Helion Light Shafts", scene, compiled.composite(), renderPass -> {
                renderPass.setUniform(LightShaftPipelines.SETTINGS, uniforms.get());
                renderPass.setUniform(LightShaftPipelines.SHAFT_SAMPLER, colorView(shafts), sampler(FilterMode.LINEAR));
                renderPass.setUniform(LightShaftPipelines.SKY_LIGHT_SAMPLER, colorView(light.irradiance()), sampler(FilterMode.NEAREST));
            });
            resources.finishFrame();
            frameUniforms = null;
        }
    }
}
