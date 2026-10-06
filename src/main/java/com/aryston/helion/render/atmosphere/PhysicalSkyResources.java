package com.aryston.helion.render.atmosphere;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntToDoubleFunction;
import java.util.stream.IntStream;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

public final class PhysicalSkyResources {
    public static final float PLANET_RADIUS_KILOMETERS = 6360.0F;
    static final float SUN_ILLUMINANCE = 16.0F;
    static final float MOON_ILLUMINANCE = 0.08F;
    static final float NO_FOG_DISTANCE = 1.0e30F;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SKY_LABEL = "Helion Sky Settings";
    private static final String SPECTRUM_LABEL = "Helion Sky Spectrum";
    private static final String FOG_LABEL = "Helion Sky Fog";
    private static final String TRANSMITTANCE_LABEL = "Helion Sky Transmittance #";
    private static final String MULTIPLE_SCATTERING_LABEL = "Helion Sky Multiple Scattering #";
    private static final String LOOKUPS_LABEL = "Helion Sky Lookup Tables";
    private static final float METERS_PER_KILOMETER = 1000.0F;
    private static final float MIN_ALTITUDE_KILOMETERS = 0.001F;
    private static final float OPAQUE = 1.0F;
    private static final int SKY_UNIFORM_SIZE = new Std140SizeCalculator()
        .putMat4f()
        .putVec3()
        .putFloat()
        .putVec3()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .get();
    private static final int SPECTRUM_VECTORS = AtmosphereSpectrum.GROUP_COUNT * (2 + AtmosphereSpectrum.RGB_CHANNELS);
    private static final int SPECTRUM_SIZE = SPECTRUM_VECTORS * Float.BYTES * AtmosphereSpectrum.CHANNELS_PER_GROUP;
    private static final int FOG_SIZE = new Std140SizeCalculator()
        .putVec4()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .get();

    private final GpuResources resources;
    private final HorizonColorReadback horizon;
    private @Nullable UniformRing uniforms;
    private @Nullable GpuBuffer spectrum;
    private @Nullable GpuBuffer fog;
    private @Nullable TrackedResource trackedBuffers;
    private List<TextureTarget> transmittance = List.of();
    private List<TextureTarget> multipleScattering = List.of();
    private @Nullable TrackedResource trackedLookups;
    private boolean lookupsWritten;
    private boolean missingPipelinesReported;

    public PhysicalSkyResources(GpuResources resources) {
        this.resources = resources;
        this.horizon = new HorizonColorReadback(resources);
    }

    public static float viewHeight(float altitudeMeters) {
        return PLANET_RADIUS_KILOMETERS + Math.max(altitudeMeters / METERS_PER_KILOMETER, MIN_ALTITUDE_KILOMETERS);
    }

    List<RenderTarget> transmittance() {
        createLookups();
        return List.copyOf(transmittance);
    }

    List<RenderTarget> multipleScattering() {
        createLookups();
        return List.copyOf(multipleScattering);
    }

    boolean lookupsWritten() {
        return lookupsWritten;
    }

    void markLookupsWritten() {
        lookupsWritten = true;
    }

    GpuBuffer spectrum() {
        createBuffers();
        return Objects.requireNonNull(spectrum);
    }

    GpuBuffer fog() {
        createBuffers();
        return Objects.requireNonNull(fog);
    }

    void writeFog(AtmosphereFog vanillaFog) {
        Vector4f color = horizon.latest()
            .map(rgb -> new Vector4f(rgb, OPAQUE))
            .orElseGet(() -> new Vector4f(vanillaFog.color()));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, FOG_SIZE)
                .putVec4(color)
                .putFloat(NO_FOG_DISTANCE)
                .putFloat(NO_FOG_DISTANCE)
                .putFloat(NO_FOG_DISTANCE)
                .putFloat(NO_FOG_DISTANCE)
                .putFloat(NO_FOG_DISTANCE)
                .putFloat(vanillaFog.cloudEnd())
                .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(fog().slice(), data);
        }
    }

    void requestHorizonColor(GpuTexture horizonTexture) {
        horizon.request(horizonTexture);
    }

    GpuBuffer writeUniforms(SkyEnvironment environment, HelionCamera camera) {
        if (uniforms == null) {
            uniforms = new UniformRing(SKY_LABEL, SKY_UNIFORM_SIZE, resources);
        }
        AtmosphereFog vanillaFog = environment.fog();
        return uniforms.write(builder -> builder
            .putMat4f(camera.levelViewProjection().invert())
            .putVec3(environment.sunDirection())
            .putFloat(SUN_ILLUMINANCE)
            .putVec3(environment.moonDirection())
            .putFloat(MOON_ILLUMINANCE)
            .putFloat(viewHeight(environment.altitude()))
            .putFloat(environment.rainBrightness())
            .putFloat(vanillaFog.environmentalStart())
            .putFloat(vanillaFog.environmentalEnd())
            .putFloat(vanillaFog.renderDistanceStart())
            .putFloat(vanillaFog.renderDistanceEnd()));
    }

    void finishFrame() {
        if (uniforms != null) {
            uniforms.rotate();
        }
    }

    void reportMissingPipelines() {
        if (!missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion physical sky is skipped because its shaders could not be compiled");
        }
    }

    public void close() {
        if (uniforms != null) {
            uniforms.close();
        }
        uniforms = null;
        release(trackedLookups);
        trackedLookups = null;
        transmittance = List.of();
        multipleScattering = List.of();
        lookupsWritten = false;
        release(trackedBuffers);
        trackedBuffers = null;
        spectrum = null;
        fog = null;
        horizon.close();
    }

    private void release(@Nullable TrackedResource tracked) {
        if (tracked != null) {
            resources.release(tracked);
        }
    }

    private void createBuffers() {
        if (spectrum != null) {
            return;
        }
        GpuBuffer createdSpectrum;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            createdSpectrum = RenderSystem.getDevice().createBuffer(() -> SPECTRUM_LABEL, GpuBuffer.USAGE_UNIFORM, spectrumData(stack));
        }
        GpuBuffer createdFog = RenderSystem.getDevice().createBuffer(
            () -> FOG_LABEL, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, FOG_SIZE
        );
        trackedBuffers = resources.track(SPECTRUM_LABEL, (long) SPECTRUM_SIZE + FOG_SIZE, () -> {
            createdSpectrum.close();
            createdFog.close();
        });
        spectrum = createdSpectrum;
        fog = createdFog;
    }

    private void createLookups() {
        if (!transmittance.isEmpty()) {
            return;
        }
        List<TextureTarget> createdTransmittance = lookupTargets(
            TRANSMITTANCE_LABEL, PhysicalSkyPipelines.TRANSMITTANCE_WIDTH, PhysicalSkyPipelines.TRANSMITTANCE_HEIGHT
        );
        List<TextureTarget> createdMultipleScattering = lookupTargets(
            MULTIPLE_SCATTERING_LABEL, PhysicalSkyPipelines.MULTIPLE_SCATTERING_SIZE, PhysicalSkyPipelines.MULTIPLE_SCATTERING_SIZE
        );
        long texels = (long) PhysicalSkyPipelines.TRANSMITTANCE_WIDTH * PhysicalSkyPipelines.TRANSMITTANCE_HEIGHT
            + (long) PhysicalSkyPipelines.MULTIPLE_SCATTERING_SIZE * PhysicalSkyPipelines.MULTIPLE_SCATTERING_SIZE;
        List<TextureTarget> all = new ArrayList<>(createdTransmittance);
        all.addAll(createdMultipleScattering);
        trackedLookups = resources.track(
            LOOKUPS_LABEL,
            texels * AtmosphereSpectrum.GROUP_COUNT * PhysicalSkyPipelines.LOOKUP_FORMAT.blockSize(),
            () -> all.forEach(TextureTarget::destroyBuffers)
        );
        transmittance = createdTransmittance;
        multipleScattering = createdMultipleScattering;
        lookupsWritten = false;
    }

    private static List<TextureTarget> lookupTargets(String label, int width, int height) {
        return IntStream.range(0, AtmosphereSpectrum.GROUP_COUNT)
            .mapToObj(group -> new TextureTarget(label + group, width, height, PhysicalSkyPipelines.LOOKUP_FORMAT, null))
            .toList();
    }

    private static ByteBuffer spectrumData(MemoryStack stack) {
        Std140Builder builder = Std140Builder.onStack(stack, SPECTRUM_SIZE);
        putGroups(builder, index -> AtmosphereSpectrum.rayleighScattering(AtmosphereSpectrum.wavelength(index)));
        putGroups(builder, index -> AtmosphereSpectrum.ozoneAbsorption(AtmosphereSpectrum.wavelength(index)));
        for (double[] channel : AtmosphereSpectrum.toLinearSrgb()) {
            putGroups(builder, index -> channel[index]);
        }
        return builder.get();
    }

    private static void putGroups(Std140Builder builder, IntToDoubleFunction valueAt) {
        for (int group = 0; group < AtmosphereSpectrum.GROUP_COUNT; group++) {
            int first = group * AtmosphereSpectrum.CHANNELS_PER_GROUP;
            builder.putVec4(
                (float) valueAt.applyAsDouble(first),
                (float) valueAt.applyAsDouble(first + 1),
                (float) valueAt.applyAsDouble(first + 2),
                (float) valueAt.applyAsDouble(first + 3)
            );
        }
    }
}
