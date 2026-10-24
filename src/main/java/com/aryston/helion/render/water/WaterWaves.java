package com.aryston.helion.render.water;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class WaterWaves {
    static final int SIZE = 1024;
    static final int CELLS_PER_BLOCK = 8;
    static final int MAX_IMPULSES = 32;
    static final int MAX_STEPS = 4;
    static final double REACH = (double) SIZE / CELLS_PER_BLOCK / 2.0;

    private static final String LABEL = "Helion Water Waves #";
    private static final String UNIFORM_LABEL = "Helion Water Wave Step #";
    private static final int STATES = 2;
    private static final double STEPS_PER_SECOND = 60.0;
    private static final double MAX_BACKLOG_SECONDS = MAX_STEPS / STEPS_PER_SECOND;
    private static final double TIME_JUMP_SECONDS = 1.0;
    private static final float WAVE_SPEED = 0.16F;
    private static final float DAMPING = 0.985F;
    private static final int HALF = SIZE / 2;
    private static final int RESET = 1;
    private static final int KEEP = 0;
    private static final int PUDDLES = 1;
    private static final float NO_IMPULSE = 0.0F;
    private static final int UNIFORM_SIZE = uniformSize();

    private final GpuResources resources;
    private final List<TextureTarget> states = new ArrayList<>();
    private final UniformRing[] uniforms = new UniformRing[MAX_STEPS];
    private final List<WaveImpulse> splashes = new ArrayList<>();
    private final WaterShoreMask shore;
    private @Nullable TrackedResource tracked;
    private int current;
    private int originX;
    private int originZ;
    private boolean valid;
    private double lastSeconds = Double.NaN;
    private double backlog;
    private int stepIndex;

    WaterWaves(GpuResources resources) {
        this.resources = resources;
        this.shore = new WaterShoreMask(resources);
    }

    int plan(double seconds) {
        double elapsed = Double.isNaN(lastSeconds) ? 0.0 : seconds - lastSeconds;
        lastSeconds = seconds;
        if (elapsed < 0.0 || elapsed > TIME_JUMP_SECONDS) {
            valid = false;
            backlog = 0.0;
            return 1;
        }
        backlog = Math.min(backlog + elapsed, MAX_BACKLOG_SECONDS);
        int steps = (int) Math.floor(backlog * STEPS_PER_SECOND);
        backlog -= steps / STEPS_PER_SECOND;
        return steps;
    }

    void queueSplashes(List<WaveImpulse> impulses) {
        impulses.stream().filter(WaveImpulse::splash).forEach(splashes::add);
    }

    TextureTarget state(int index) {
        if (states.isEmpty()) {
            for (int state = 0; state < STATES; state++) {
                states.add(new TextureTarget(LABEL + state, SIZE, SIZE, WaterPipelines.WAVE_FORMAT, null));
            }
            List<TextureTarget> created = List.copyOf(states);
            tracked = resources.track(LABEL, (long) STATES * SIZE * SIZE * WaterPipelines.WAVE_FORMAT.blockSize(), () -> created.forEach(TextureTarget::destroyBuffers));
            valid = false;
        }
        return states.get(index);
    }

    WaterShoreMask shore() {
        return shore;
    }

    int current() {
        return current;
    }

    GpuBuffer stepUniforms(int step, double cameraX, double cameraZ, List<WaveImpulse> impulses, boolean puddles) {
        if (uniforms[step] == null) {
            uniforms[step] = new UniformRing(UNIFORM_LABEL + step, UNIFORM_SIZE, resources);
        }
        int previousX = originX;
        int previousZ = originZ;
        originX = (int) Math.floor(cameraX * CELLS_PER_BLOCK) - HALF;
        originZ = (int) Math.floor(cameraZ * CELLS_PER_BLOCK) - HALF;
        boolean reset = !valid;
        valid = true;
        List<WaveImpulse> applied = new ArrayList<>(impulses.stream().filter(impulse -> !impulse.splash()).toList());
        if (step == 0) {
            applied.addAll(splashes);
            splashes.clear();
        }
        int count = Math.min(applied.size(), MAX_IMPULSES);
        int seed = stepIndex++;
        return uniforms[step].write(builder -> {
            builder.putIVec4(originX, originZ, reset ? originX : previousX, reset ? originZ : previousZ)
                .putVec4(WAVE_SPEED, DAMPING, NO_IMPULSE, NO_IMPULSE)
                .putIVec4(count, seed, reset ? RESET : KEEP, puddles ? PUDDLES : KEEP);
            for (int index = 0; index < MAX_IMPULSES; index++) {
                putImpulse(builder, index < count ? applied.get(index) : null);
            }
        });
    }

    void finishStep() {
        current = (current + 1) % STATES;
    }

    void finishFrame() {
        for (UniformRing ring : uniforms) {
            if (ring != null) {
                ring.rotate();
            }
        }
    }

    float originBlockX() {
        return (float) originX / CELLS_PER_BLOCK;
    }

    float originBlockZ() {
        return (float) originZ / CELLS_PER_BLOCK;
    }

    void close() {
        shore.close();
        if (tracked != null) {
            resources.release(tracked);
        }
        for (int step = 0; step < MAX_STEPS; step++) {
            if (uniforms[step] != null) {
                uniforms[step].close();
                uniforms[step] = null;
            }
        }
        tracked = null;
        states.clear();
        splashes.clear();
        valid = false;
    }

    private void putImpulse(Std140Builder builder, @Nullable WaveImpulse impulse) {
        if (impulse == null) {
            builder.putVec4(NO_IMPULSE, NO_IMPULSE, NO_IMPULSE, NO_IMPULSE);
            return;
        }
        builder.putVec4(
            (float) (impulse.x() * CELLS_PER_BLOCK - originX),
            (float) (impulse.z() * CELLS_PER_BLOCK - originZ),
            impulse.radius() * CELLS_PER_BLOCK,
            impulse.strength()
        );
    }

    private static int uniformSize() {
        Std140SizeCalculator calculator = new Std140SizeCalculator().putIVec4().putVec4().putIVec4();
        for (int index = 0; index < MAX_IMPULSES; index++) {
            calculator.putVec4();
        }
        return calculator.get();
    }
}
