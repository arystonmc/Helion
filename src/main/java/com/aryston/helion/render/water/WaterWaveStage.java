package com.aryston.helion.render.water;

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
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.List;
import java.util.Objects;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;

public final class WaterWaveStage implements RenderStage {
    private static final String NAME = "water_waves";
    private static final String PASS_LABEL = "Helion Water Wave Step";
    private static final String STATE_PREFIX = "helion:water_waves_";
    private static final double NANOS_PER_SECOND = 1.0e9;
    private static final double IMPULSE_MARGIN = 8.0;
    private static final int STATES = 2;
    private static final float PUDDLE_WAVES = 0.05F;

    private final WaveSource source;
    private final WaterResources resources;
    private @Nullable CompiledRenderPipeline program;

    public WaterWaveStage(WaveSource source, WaterResources resources) {
        this.source = source;
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().reflections().realisticWater()) {
            resources.waves().close();
            return false;
        }
        program = HelionPipelines.compiled(WaterPipelines.WAVE_STEP).orElse(null);
        return program != null;
    }

    @Override
    public void addTo(FrameContext frame) {
        CompiledRenderPipeline pipeline = Objects.requireNonNull(program);
        WaterWaves waves = resources.waves();
        Vector3dc camera = frame.camera().position();
        boolean puddles = frame.water().wetness().map(wetness -> wetness.puddles() > PUDDLE_WAVES).orElse(false);
        List<WaveImpulse> impulses = source.impulses(camera.x(), camera.z(), WaterWaves.REACH - IMPULSE_MARGIN, puddles);
        waves.queueSplashes(impulses);
        int steps = waves.plan(System.nanoTime() / NANOS_PER_SECOND);
        float rain = frame.water().wetness().map(Wetness::rain).orElse(0.0F);
        FramePass pass = frame.graph().addPass(NAME);
        int first = waves.current();
        ResourceHandle<RenderTarget> stateA = pass.readsAndWrites(frame.graph().builder().importExternal(STATE_PREFIX + first, waves.state(first)));
        int second = (first + 1) % STATES;
        ResourceHandle<RenderTarget> stateB = pass.readsAndWrites(frame.graph().builder().importExternal(STATE_PREFIX + second, waves.state(second)));
        frame.water().publishWaves(steps % STATES == 0 ? stateA : stateB);
        double cameraX = camera.x();
        double cameraY = camera.y();
        double cameraZ = camera.z();
        pass.executes(frame.graph().timed(NAME, () -> {
            GpuTextureView shore = waves.shore().refresh(source, cameraX, cameraY, cameraZ);
            for (int step = 0; step < steps; step++) {
                RenderTarget read = step % STATES == 0 ? stateA.get() : stateB.get();
                RenderTarget written = step % STATES == 0 ? stateB.get() : stateA.get();
                int index = step;
                FullscreenPass.draw(PASS_LABEL, written, pipeline, renderPass -> {
                    renderPass.setUniform(WaterPipelines.WAVE_UNIFORM, waves.stepUniforms(index, cameraX, cameraZ, impulses, rain, puddles));
                    renderPass.setUniform(WaterPipelines.SHORE_SAMPLER, shore, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                    renderPass.setUniform(
                        WaterPipelines.WAVE_SAMPLER,
                        Objects.requireNonNull(read.getColorTextureView()),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                    );
                });
                waves.finishStep();
            }
            waves.finishFrame();
        }));
    }
}
