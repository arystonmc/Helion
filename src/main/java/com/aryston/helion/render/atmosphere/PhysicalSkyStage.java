package com.aryston.helion.render.atmosphere;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

public final class PhysicalSkyStage implements RenderStage {
    private static final String NAME = "sky";
    private static final String TARGET_PREFIX = "helion:";
    private static final String LABEL_PREFIX = "Helion ";
    private static final String TRANSMITTANCE_PASS = "sky_transmittance";
    private static final String MULTIPLE_SCATTERING_PASS = "sky_multiple_scattering";
    private static final String SUN_SKY_VIEW_PASS = "sky_view_sun";
    private static final String MOON_SKY_VIEW_PASS = "sky_view_moon";
    private static final String HORIZON_PASS = "sky_horizon";
    private static final String SKY_LIGHT_PASS = "sky_light";
    private static final String FOG_LABEL = "Helion Sky Fog";
    private static final String MIE_SUFFIX = "_mie";

    private final AtmosphereSource atmosphere;
    private final PhysicalSkyResources resources;
    private @Nullable PhysicalSkyPrograms programs;
    private @Nullable SkyEnvironment environment;
    private @Nullable GpuBuffer frameUniforms;

    public PhysicalSkyStage(AtmosphereSource atmosphere, PhysicalSkyResources resources) {
        this.atmosphere = atmosphere;
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().sky().enabled()
            || !frame.scene().renderSky()
            || frame.scene().colorFormat() != PhysicalSkyPipelines.SCENE_COLOR_FORMAT
            || !atmosphere.hasSky()) {
            return false;
        }
        environment = atmosphere.skyEnvironment().orElse(null);
        if (environment == null) {
            return false;
        }
        Optional<PhysicalSkyPrograms> compiled = PhysicalSkyPrograms.compile();
        if (compiled.isEmpty()) {
            resources.reportMissingPipelines();
            return false;
        }
        programs = compiled.get();
        return true;
    }

    @Override
    public void addTo(FrameContext frame) {
        PhysicalSkyPrograms compiled = Objects.requireNonNull(programs);
        SkyEnvironment sky = Objects.requireNonNull(environment);
        List<ResourceHandle<RenderTarget>> transmittance = imported(frame, TRANSMITTANCE_PASS, resources.transmittance());
        List<ResourceHandle<RenderTarget>> multipleScattering = imported(frame, MULTIPLE_SCATTERING_PASS, resources.multipleScattering());
        if (!resources.lookupsWritten()) {
            transmittance = addConstantLookupPass(frame, TRANSMITTANCE_PASS, transmittance, compiled.transmittance(), List.of(), () -> { });
            multipleScattering = addConstantLookupPass(
                frame, MULTIPLE_SCATTERING_PASS, multipleScattering, compiled.multipleScattering(),
                inputs(PhysicalSkyPipelines.TRANSMITTANCE_SAMPLERS, transmittance), resources::markLookupsWritten
            );
        }
        List<LookupInput> skyViewInputs = new ArrayList<>(inputs(PhysicalSkyPipelines.TRANSMITTANCE_SAMPLERS, transmittance));
        skyViewInputs.addAll(inputs(PhysicalSkyPipelines.MULTIPLE_SCATTERING_SAMPLERS, multipleScattering));
        List<ResourceHandle<RenderTarget>> sunSkyView = addSkyViewPass(frame, SUN_SKY_VIEW_PASS, compiled.sunSkyView(), sky, skyViewInputs);
        List<ResourceHandle<RenderTarget>> moonSkyView = addSkyViewPass(frame, MOON_SKY_VIEW_PASS, compiled.moonSkyView(), sky, skyViewInputs);
        List<LookupInput> skyColorInputs = List.of(
            new LookupInput(PhysicalSkyPipelines.SUN_SKY_VIEW_SAMPLER, sunSkyView.get(PhysicalSkyPipelines.SKY_VIEW_SCATTERING_TARGET)),
            new LookupInput(PhysicalSkyPipelines.SUN_MIE_VIEW_SAMPLER, sunSkyView.get(PhysicalSkyPipelines.SKY_VIEW_MIE_TARGET)),
            new LookupInput(PhysicalSkyPipelines.MOON_SKY_VIEW_SAMPLER, moonSkyView.get(PhysicalSkyPipelines.SKY_VIEW_SCATTERING_TARGET)),
            new LookupInput(PhysicalSkyPipelines.MOON_MIE_VIEW_SAMPLER, moonSkyView.get(PhysicalSkyPipelines.SKY_VIEW_MIE_TARGET))
        );
        addHorizonPass(frame, compiled, sky, skyColorInputs);
        List<LookupInput> skyLightInputs = new ArrayList<>(skyColorInputs);
        skyLightInputs.addAll(inputs(PhysicalSkyPipelines.TRANSMITTANCE_SAMPLERS, transmittance));
        frame.atmosphere().publishSkyLight(new SkyLight(
            addSkyLightPass(frame, compiled, sky, skyLightInputs), sky.sunDirection(), sky.moonDirection()
        ));
        atmosphere.useFog(resources.fog().slice());
        addSkyPass(frame, compiled, sky, skyColorInputs);
        frame.atmosphere().publishFog(new AerialFog(frame, compiled, sky, skyColorInputs));
    }

    private List<ResourceHandle<RenderTarget>> addSkyViewPass(
        FrameContext frame,
        String name,
        CompiledRenderPipeline pipeline,
        SkyEnvironment sky,
        List<LookupInput> inputs
    ) {
        FramePass pass = frame.graph().addPass(name);
        inputs.forEach(input -> pass.reads(input.target()));
        List<ResourceHandle<RenderTarget>> targets = List.of(
            createInternal(pass, name, PhysicalSkyPipelines.SKY_VIEW_WIDTH, PhysicalSkyPipelines.SKY_VIEW_HEIGHT),
            createInternal(pass, name + MIE_SUFFIX, PhysicalSkyPipelines.SKY_VIEW_WIDTH, PhysicalSkyPipelines.SKY_VIEW_HEIGHT)
        );
        pass.executes(frame.graph().timed(name, () -> {
            List<RenderTarget> written = targets.stream().map(ResourceHandle::get).toList();
            FullscreenPass.draw(LABEL_PREFIX + name, written, pipeline, renderPass -> {
                renderPass.setUniform(PhysicalSkyPipelines.SETTINGS, uniforms(sky, frame));
                renderPass.setUniform(PhysicalSkyPipelines.SPECTRUM, resources.spectrum());
                bind(renderPass, inputs);
            });
        }));
        return targets;
    }

    private List<ResourceHandle<RenderTarget>> addConstantLookupPass(
        FrameContext frame,
        String name,
        List<ResourceHandle<RenderTarget>> persistent,
        CompiledRenderPipeline pipeline,
        List<LookupInput> inputs,
        Runnable afterDraw
    ) {
        FramePass pass = frame.graph().addPass(name);
        inputs.forEach(input -> pass.reads(input.target()));
        List<ResourceHandle<RenderTarget>> written = persistent.stream().map(pass::readsAndWrites).toList();
        pass.executes(frame.graph().timed(name, () -> {
            List<RenderTarget> targets = written.stream().map(ResourceHandle::get).toList();
            FullscreenPass.draw(LABEL_PREFIX + name, targets, pipeline, renderPass -> {
                renderPass.setUniform(PhysicalSkyPipelines.SPECTRUM, resources.spectrum());
                bind(renderPass, inputs);
            });
            afterDraw.run();
        }));
        return written;
    }

    private void addHorizonPass(FrameContext frame, PhysicalSkyPrograms compiled, SkyEnvironment sky, List<LookupInput> inputs) {
        FramePass pass = frame.graph().addPass(HORIZON_PASS);
        pass.disableCulling();
        inputs.forEach(input -> pass.reads(input.target()));
        ResourceHandle<RenderTarget> target = pass.createsInternal(TARGET_PREFIX + HORIZON_PASS, new RenderTargetDescriptor(
            PhysicalSkyPipelines.HORIZON_SIZE,
            PhysicalSkyPipelines.HORIZON_SIZE,
            new RenderTargetDescriptor.TextureProperties(null, PhysicalSkyPipelines.SCENE_COLOR_FORMAT),
            null
        ));
        pass.executes(frame.graph().timed(HORIZON_PASS, () -> {
            FullscreenPass.draw(LABEL_PREFIX + HORIZON_PASS, target.get(), compiled.horizon(), renderPass -> {
                renderPass.setUniform(PhysicalSkyPipelines.SETTINGS, uniforms(sky, frame));
                bind(renderPass, inputs);
            });
            resources.requestHorizonColor(Objects.requireNonNull(target.get().getColorTexture()));
        }));
    }

    private ResourceHandle<RenderTarget> addSkyLightPass(
        FrameContext frame,
        PhysicalSkyPrograms compiled,
        SkyEnvironment sky,
        List<LookupInput> inputs
    ) {
        FramePass pass = frame.graph().addPass(SKY_LIGHT_PASS);
        inputs.forEach(input -> pass.reads(input.target()));
        ResourceHandle<RenderTarget> target = createInternal(
            pass, SKY_LIGHT_PASS, PhysicalSkyPipelines.SKY_LIGHT_WIDTH, PhysicalSkyPipelines.SKY_LIGHT_HEIGHT
        );
        pass.executes(frame.graph().timed(SKY_LIGHT_PASS, () -> FullscreenPass.draw(
            LABEL_PREFIX + SKY_LIGHT_PASS, target.get(), compiled.skyLight(), renderPass -> {
                renderPass.setUniform(PhysicalSkyPipelines.SETTINGS, uniforms(sky, frame));
                renderPass.setUniform(PhysicalSkyPipelines.SPECTRUM, resources.spectrum());
                bind(renderPass, inputs);
            })));
        return target;
    }

    private void addSkyPass(FrameContext frame, PhysicalSkyPrograms compiled, SkyEnvironment sky, List<LookupInput> inputs) {
        FramePass pass = frame.graph().addPass(NAME);
        inputs.forEach(input -> pass.reads(input.target()));
        ResourceHandle<RenderTarget> scene = pass.readsAndWrites(frame.targets().scene());
        frame.targets().updateScene(scene);
        pass.executes(frame.graph().timed(NAME, () -> {
            resources.writeFog(sky.fog());
            FullscreenPass.draw("Helion Physical Sky", scene.get(), compiled.sky(), renderPass -> {
                renderPass.setUniform(PhysicalSkyPipelines.SETTINGS, uniforms(sky, frame));
                bind(renderPass, inputs);
            });
            atmosphere.renderCelestials(scene.get());
        }));
    }

    private GpuBuffer uniforms(SkyEnvironment sky, FrameContext frame) {
        if (frameUniforms == null) {
            frameUniforms = resources.writeUniforms(sky, frame.camera());
        }
        return frameUniforms;
    }

    private void finishFrame() {
        resources.finishFrame();
        frameUniforms = null;
    }

    private static List<ResourceHandle<RenderTarget>> imported(FrameContext frame, String name, List<RenderTarget> targets) {
        return IntStream.range(0, targets.size())
            .mapToObj(group -> frame.graph().builder().importExternal(TARGET_PREFIX + name + "_" + group, targets.get(group)))
            .toList();
    }

    private static List<LookupInput> inputs(List<String> samplers, List<ResourceHandle<RenderTarget>> targets) {
        return IntStream.range(0, samplers.size()).mapToObj(group -> new LookupInput(samplers.get(group), targets.get(group))).toList();
    }

    private static ResourceHandle<RenderTarget> createInternal(FramePass pass, String name, int width, int height) {
        return pass.createsInternal(TARGET_PREFIX + name, new RenderTargetDescriptor(
            width, height, new RenderTargetDescriptor.TextureProperties(null, PhysicalSkyPipelines.LOOKUP_FORMAT), null
        ));
    }

    private static void bind(RenderPass renderPass, List<LookupInput> inputs) {
        inputs.forEach(input -> renderPass.setUniform(input.sampler(), colorView(input.target()), linear()));
    }

    private static GpuTextureView colorView(ResourceHandle<RenderTarget> handle) {
        return Objects.requireNonNull(handle.get().getColorTextureView());
    }

    private static GpuSampler linear() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    private static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    private record LookupInput(String sampler, ResourceHandle<RenderTarget> target) {
    }

    private final class AerialFog implements SceneFogPass {
        private final FrameContext frame;
        private final PhysicalSkyPrograms compiled;
        private final SkyEnvironment sky;
        private final List<LookupInput> inputs;

        private AerialFog(FrameContext frame, PhysicalSkyPrograms compiled, SkyEnvironment sky, List<LookupInput> inputs) {
            this.frame = frame;
            this.compiled = compiled;
            this.sky = sky;
            this.inputs = inputs;
        }

        @Override
        public void declareReads(FramePass pass) {
            inputs.forEach(input -> pass.reads(input.target()));
        }

        @Override
        public void draw(RenderTarget scene) {
            Consumer<RenderPass> bindings = renderPass -> {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform(PhysicalSkyPipelines.SETTINGS, uniforms(sky, frame));
                renderPass.setUniform(PhysicalSkyPipelines.DEPTH_SAMPLER, Objects.requireNonNull(scene.getDepthTextureView()), nearest());
                bind(renderPass, inputs);
            };
            FullscreenPass.draw(FOG_LABEL, scene, compiled.fog(), bindings);
            finishFrame();
        }
    }
}
