package com.aryston.helion.render.clouds;

import com.aryston.helion.render.atmosphere.SkyLight;
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
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class CloudStage implements RenderStage {
    private static final String NAME = "clouds";
    private static final String NOISE_PASS = "cloud_noise";
    private static final String MARCH_PASS = "cloud_march";
    private static final String RESOLVE_PASS = "cloud_resolve";
    private static final String TARGET_PREFIX = "helion:";

    private final CloudSource source;
    private final CloudResources resources;
    private @Nullable CloudPrograms programs;
    private @Nullable CloudEnvironment environment;
    private @Nullable SkyLight skyLight;
    private @Nullable GpuBuffer frameUniforms;

    public CloudStage(CloudSource source, CloudResources resources) {
        this.source = source;
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        boolean active = checkActive(frame);
        source.replaceVanillaClouds(active);
        if (!active) {
            resources.invalidate();
        }
        return active;
    }

    private boolean checkActive(FrameContext frame) {
        CloudSettings settings = frame.settings().clouds();
        if (!settings.enabled() || frame.scene().colorFormat() != CloudPipelines.SCENE_COLOR_FORMAT) {
            return false;
        }
        skyLight = frame.atmosphere().skyLight().orElse(null);
        environment = source.environment().orElse(null);
        if (skyLight == null || environment == null) {
            return false;
        }
        programs = resources.programs().orElse(null);
        return programs != null;
    }

    @Override
    public void addTo(FrameContext frame) {
        CloudPrograms compiled = Objects.requireNonNull(programs);
        CloudQuality quality = frame.settings().clouds().quality();
        int width = Math.max(1, frame.scene().width() / quality.resolutionDivisor());
        int height = Math.max(1, frame.scene().height() / quality.resolutionDivisor());
        resources.ensureHistory(width, height);
        Uniforms uniforms = new Uniforms(frame, quality, width, height);
        ResourceHandle<RenderTarget> noise = frame.graph().builder().importExternal(TARGET_PREFIX + NOISE_PASS, resources.noise());
        if (!resources.noiseWritten()) {
            noise = addNoisePass(frame, compiled, noise);
        }
        List<ResourceHandle<RenderTarget>> marched = addMarchPass(frame, compiled, noise, uniforms, width, height);
        CloudResources.History previous = resources.previousHistory();
        CloudResources.History next = resources.nextHistory();
        ResourceHandle<RenderTarget> previousColor = frame.graph().builder().importExternal(TARGET_PREFIX + "cloud_history_color_previous", previous.color());
        ResourceHandle<RenderTarget> previousDistance = frame.graph().builder().importExternal(
            TARGET_PREFIX + "cloud_history_distance_previous", previous.distance()
        );
        ResourceHandle<RenderTarget> nextColor = frame.graph().builder().importExternal(TARGET_PREFIX + "cloud_history_color_next", next.color());
        ResourceHandle<RenderTarget> nextDistance = frame.graph().builder().importExternal(TARGET_PREFIX + "cloud_history_distance_next", next.distance());

        FramePass pass = frame.graph().addPass(RESOLVE_PASS);
        marched.forEach(pass::reads);
        pass.reads(previousColor);
        pass.reads(previousDistance);
        ResourceHandle<RenderTarget> resolvedColor = pass.readsAndWrites(nextColor);
        ResourceHandle<RenderTarget> resolvedDistance = pass.readsAndWrites(nextDistance);
        pass.executes(frame.graph().timed(RESOLVE_PASS, () -> FullscreenPass.draw(
            "Helion Cloud Resolve", List.of(resolvedColor.get(), resolvedDistance.get()), compiled.resolve(), renderPass -> {
                renderPass.setUniform(CloudPipelines.SETTINGS, uniforms.get());
                renderPass.setUniform(CloudPipelines.COLOR_SAMPLER, colorView(marched.get(CloudPipelines.COLOR_TARGET)), sampler(FilterMode.NEAREST));
                renderPass.setUniform(CloudPipelines.DISTANCE_SAMPLER, colorView(marched.get(CloudPipelines.DISTANCE_TARGET)), sampler(FilterMode.NEAREST));
                renderPass.setUniform(CloudPipelines.HISTORY_COLOR_SAMPLER, colorView(previousColor), sampler(FilterMode.LINEAR));
                renderPass.setUniform(CloudPipelines.HISTORY_DISTANCE_SAMPLER, colorView(previousDistance), sampler(FilterMode.LINEAR));
            })));
        frame.atmosphere().publishClouds(new Composite(compiled, uniforms, resolvedColor));
    }

    private ResourceHandle<RenderTarget> addNoisePass(FrameContext frame, CloudPrograms compiled, ResourceHandle<RenderTarget> noise) {
        FramePass pass = frame.graph().addPass(NOISE_PASS);
        ResourceHandle<RenderTarget> written = pass.readsAndWrites(noise);
        pass.executes(frame.graph().timed(NOISE_PASS, () -> {
            FullscreenPass.draw("Helion Cloud Noise", written.get(), compiled.noise(), renderPass -> { });
            resources.markNoiseWritten();
        }));
        return written;
    }

    private List<ResourceHandle<RenderTarget>> addMarchPass(
        FrameContext frame,
        CloudPrograms compiled,
        ResourceHandle<RenderTarget> noise,
        Uniforms uniforms,
        int width,
        int height
    ) {
        FramePass pass = frame.graph().addPass(MARCH_PASS);
        pass.reads(noise);
        SkyLight light = Objects.requireNonNull(skyLight);
        pass.reads(light.irradiance());
        ResourceHandle<RenderTarget> color = pass.createsInternal(TARGET_PREFIX + "cloud_color", target(width, height, CloudPipelines.COLOR_FORMAT));
        ResourceHandle<RenderTarget> distance = pass.createsInternal(
            TARGET_PREFIX + "cloud_distance", target(width, height, CloudPipelines.DISTANCE_FORMAT)
        );
        pass.executes(frame.graph().timed(MARCH_PASS, () -> FullscreenPass.draw(
            "Helion Cloud March", List.of(color.get(), distance.get()), compiled.march(), renderPass -> {
                renderPass.setUniform(CloudPipelines.SETTINGS, uniforms.get());
                renderPass.setUniform(CloudPipelines.NOISE_SAMPLER, colorView(noise), sampler(FilterMode.LINEAR));
                renderPass.setUniform(CloudPipelines.SKY_LIGHT_SAMPLER, colorView(light.irradiance()), sampler(FilterMode.NEAREST));
            })));
        return List.of(color, distance);
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
        private final CloudQuality quality;
        private final int width;
        private final int height;
        private final CloudEnvironment clouds;
        private final SkyLight light;

        private Uniforms(FrameContext frame, CloudQuality quality, int width, int height) {
            this.frame = frame;
            this.quality = quality;
            this.width = width;
            this.height = height;
            this.clouds = Objects.requireNonNull(environment);
            this.light = Objects.requireNonNull(skyLight);
        }

        GpuBuffer get() {
            if (frameUniforms == null) {
                frameUniforms = resources.writeUniforms(
                    frame.camera(), light.sunDirection(), light.moonDirection(), clouds, quality, width, height
                );
            }
            return frameUniforms;
        }
    }

    private final class Composite implements SceneCloudPass {
        private final CloudPrograms compiled;
        private final Uniforms uniforms;
        private final ResourceHandle<RenderTarget> resolved;

        private Composite(CloudPrograms compiled, Uniforms uniforms, ResourceHandle<RenderTarget> resolved) {
            this.compiled = compiled;
            this.uniforms = uniforms;
            this.resolved = resolved;
        }

        @Override
        public void declareReads(FramePass pass) {
            pass.reads(resolved);
        }

        @Override
        public void draw(RenderTarget scene) {
            FullscreenPass.draw("Helion Clouds", scene, compiled.composite(), renderPass -> {
                renderPass.setUniform(CloudPipelines.SETTINGS, uniforms.get());
                renderPass.setUniform(CloudPipelines.COLOR_SAMPLER, colorView(resolved), sampler(FilterMode.LINEAR));
                renderPass.setUniform(CloudPipelines.DEPTH_SAMPLER, Objects.requireNonNull(scene.getDepthTextureView()), sampler(FilterMode.NEAREST));
            });
            resources.finishFrame();
            frameUniforms = null;
        }
    }
}
