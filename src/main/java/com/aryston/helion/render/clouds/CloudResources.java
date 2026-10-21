package com.aryston.helion.render.clouds;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class CloudResources {
    static final float LAYER_BOTTOM = 600.0F;
    static final float LAYER_TOP = 1100.0F;
    static final float EXTINCTION = 0.045F;
    static final float COVERAGE = 0.45F;
    static final float MAX_DISTANCE = 40000.0F;
    static final float FADE_START = 14000.0F;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SETTINGS_LABEL = "Helion Cloud Settings";
    private static final String NOISE_LABEL = "Helion Cloud Noise";
    private static final String HISTORY_LABEL = "Helion Cloud History";
    private static final double NOISE_PERIOD = 24576.0;
    private static final double TIME_PERIOD = 6144.0;
    private static final double TELEPORT_DISTANCE = 64.0;
    private static final int HISTORY_COUNT = 2;
    private static final int FRAME_CYCLE = 64;
    private static final float SUN_BELOW_HORIZON = -0.05F;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putMat4f()
        .putMat4f()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .get();

    private final GpuResources resources;
    private @Nullable UniformRing uniforms;
    private @Nullable TextureTarget noise;
    private @Nullable TrackedResource trackedNoise;
    private List<History> history = List.of();
    private @Nullable TrackedResource trackedHistory;
    private @Nullable Matrix4f previousViewProjection;
    private @Nullable Vector3d previousPosition;
    private boolean noiseWritten;
    private boolean historyWritten;
    private int written;
    private int frameIndex;
    private boolean missingPipelinesReported;

    public CloudResources(GpuResources resources) {
        this.resources = resources;
    }

    Optional<CloudPrograms> programs() {
        Optional<CloudPrograms> compiled = CloudPrograms.compile();
        if (compiled.isEmpty() && !missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion volumetric clouds are skipped because their shaders could not be compiled");
        }
        return compiled;
    }

    RenderTarget noise() {
        if (noise == null) {
            int size = CloudPipelines.NOISE_ATLAS_SIZE;
            TextureTarget created = new TextureTarget(NOISE_LABEL, size, size, CloudPipelines.NOISE_FORMAT, null);
            trackedNoise = resources.track(NOISE_LABEL, (long) size * size * CloudPipelines.NOISE_FORMAT.blockSize(), created::destroyBuffers);
            noise = created;
            noiseWritten = false;
        }
        return noise;
    }

    boolean noiseWritten() {
        return noiseWritten;
    }

    void markNoiseWritten() {
        noiseWritten = true;
    }

    void ensureHistory(int width, int height) {
        if (!history.isEmpty() && history.getFirst().color().width == width && history.getFirst().color().height == height) {
            return;
        }
        releaseHistory();
        List<History> created = IntStream.range(0, HISTORY_COUNT)
            .mapToObj(index -> new History(
                new TextureTarget(HISTORY_LABEL + " Color #" + index, width, height, CloudPipelines.COLOR_FORMAT, null),
                new TextureTarget(HISTORY_LABEL + " Distance #" + index, width, height, CloudPipelines.DISTANCE_FORMAT, null)
            ))
            .toList();
        long bytes = (long) width * height * (CloudPipelines.COLOR_FORMAT.blockSize() + CloudPipelines.DISTANCE_FORMAT.blockSize()) * HISTORY_COUNT;
        trackedHistory = resources.track(HISTORY_LABEL, bytes, () -> created.forEach(History::destroy));
        history = created;
        historyWritten = false;
    }

    History previousHistory() {
        return history.get(written);
    }

    History nextHistory() {
        return history.get((written + 1) % HISTORY_COUNT);
    }

    GpuBuffer writeUniforms(HelionCamera camera, Vector3fc sunDirection, Vector3fc moonDirection, CloudEnvironment environment,
        CloudQuality quality, int width, int height) {
        if (uniforms == null) {
            uniforms = new UniformRing(SETTINGS_LABEL, UNIFORM_SIZE, resources);
        }
        Matrix4f viewProjection = camera.levelViewProjection();
        Vector3dc position = camera.position();
        boolean valid = historyWritten
            && previousViewProjection != null
            && previousPosition != null
            && previousPosition.distance(position) < TELEPORT_DISTANCE;
        Matrix4fc previous = valid ? previousViewProjection : viewProjection;
        Vector3d motion = valid ? new Vector3d(position).sub(previousPosition) : new Vector3d();
        boolean sunLit = sunDirection.y() > SUN_BELOW_HORIZON;
        Vector3fc light = sunLit ? sunDirection : moonDirection;
        float seconds = (float) (environment.seconds() % TIME_PERIOD);
        int frame = frameIndex++ % FRAME_CYCLE;
        previousViewProjection = new Matrix4f(viewProjection);
        previousPosition = new Vector3d(position);
        Matrix4f inverse = new Matrix4f(viewProjection).invert();
        return uniforms.write(builder -> builder
            .putMat4f(inverse)
            .putMat4f(previous)
            .putVec4((float) wrap(position.x()), (float) position.y(), (float) wrap(position.z()), 0.0F)
            .putVec4((float) motion.x, (float) motion.y, (float) motion.z, valid ? 1.0F : 0.0F)
            .putVec4(light.x(), light.y(), light.z(), sunLit ? 1.0F : 0.0F)
            .putVec4(LAYER_BOTTOM, LAYER_TOP, EXTINCTION, COVERAGE)
            .putVec4(quality.primarySteps(), quality.lightSteps(), quality.cirrus() ? 1.0F : 0.0F, frame)
            .putVec4(environment.rain(), seconds, MAX_DISTANCE, FADE_START)
            .putVec4(width, height, 1.0F / width, 1.0F / height));
    }

    void finishFrame() {
        written = (written + 1) % HISTORY_COUNT;
        historyWritten = true;
        if (uniforms != null) {
            uniforms.rotate();
        }
    }

    public void invalidate() {
        historyWritten = false;
        previousViewProjection = null;
        previousPosition = null;
    }

    public void close() {
        if (uniforms != null) {
            uniforms.close();
        }
        uniforms = null;
        if (trackedNoise != null) {
            resources.release(trackedNoise);
        }
        trackedNoise = null;
        noise = null;
        noiseWritten = false;
        releaseHistory();
        invalidate();
    }

    private void releaseHistory() {
        if (trackedHistory != null) {
            resources.release(trackedHistory);
        }
        trackedHistory = null;
        history = List.of();
        historyWritten = false;
    }

    private static double wrap(double coordinate) {
        return coordinate - Math.floor(coordinate / NOISE_PERIOD) * NOISE_PERIOD;
    }

    record History(TextureTarget color, TextureTarget distance) {
        void destroy() {
            color.destroyBuffers();
            distance.destroyBuffers();
        }
    }
}
