package com.aryston.helion.render.atmosphere;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public final class PhysicalSkyPipelines {
    public static final String FOG = "Fog";
    public static final String SETTINGS = "HelionSky";
    public static final String SPECTRUM = "HelionSpectrum";
    public static final String SUN_SKY_VIEW_SAMPLER = "SunSkyViewSampler";
    public static final String SUN_MIE_VIEW_SAMPLER = "SunMieViewSampler";
    public static final String MOON_SKY_VIEW_SAMPLER = "MoonSkyViewSampler";
    public static final String MOON_MIE_VIEW_SAMPLER = "MoonMieViewSampler";
    public static final String DEPTH_SAMPLER = "DepthSampler";
    public static final List<String> TRANSMITTANCE_SAMPLERS = samplers("TransmittanceSampler");
    public static final List<String> MULTIPLE_SCATTERING_SAMPLERS = samplers("MultipleScatteringSampler");
    public static final GpuFormat LOOKUP_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final int TRANSMITTANCE_WIDTH = 256;
    public static final int TRANSMITTANCE_HEIGHT = 64;
    public static final int MULTIPLE_SCATTERING_SIZE = 32;
    public static final int SKY_VIEW_WIDTH = 192;
    public static final int SKY_VIEW_HEIGHT = 108;
    public static final int SKY_VIEW_SCATTERING_TARGET = 0;
    public static final int SKY_VIEW_MIE_TARGET = 1;
    public static final int HORIZON_SIZE = 1;
    public static final int SKY_LIGHT_WIDTH = 3;
    public static final int SKY_LIGHT_HEIGHT = 1;
    private static final String SKY_VIEW_SHADER = "atmosphere/sky_view";
    private static final String MOON_VIEW_DEFINE = "HELION_MOON_VIEW";

    public static final RenderPipeline TRANSMITTANCE = spectralTargets(HelionPipelines.fullscreen("atmosphere/transmittance")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SPECTRUM, UniformType.UNIFORM_BUFFER)
            .build()))
        .build();

    public static final RenderPipeline MULTIPLE_SCATTERING = spectralTargets(HelionPipelines.fullscreen("atmosphere/multiple_scattering")
        .withBindGroupLayout(withSamplers(BindGroupLayout.builder()
            .withUniform(SPECTRUM, UniformType.UNIFORM_BUFFER), TRANSMITTANCE_SAMPLERS)
            .build()))
        .build();

    public static final RenderPipeline SUN_SKY_VIEW = skyView("atmosphere/sun_sky_view").build();

    public static final RenderPipeline MOON_SKY_VIEW = skyView("atmosphere/moon_sky_view")
        .withShaderDefine(MOON_VIEW_DEFINE)
        .build();

    public static final RenderPipeline SKY = HelionPipelines.fullscreen("atmosphere/sky")
        .withBindGroupLayout(skyColorBindings().build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_ALL))
        .build();

    public static final RenderPipeline FOG_PASS = HelionPipelines.fullscreen("atmosphere/fog")
        .withBindGroupLayout(skyColorBindings()
            .withUniform(FOG, UniformType.UNIFORM_BUFFER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_COLOR))
        .build();

    public static final RenderPipeline HORIZON = HelionPipelines.fullscreen("atmosphere/horizon")
        .withBindGroupLayout(skyColorBindings().build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_ALL))
        .build();

    public static final RenderPipeline SKY_LIGHT = HelionPipelines.fullscreen("atmosphere/sky_light")
        .withBindGroupLayout(withSamplers(skyColorBindings()
            .withUniform(SPECTRUM, UniformType.UNIFORM_BUFFER), TRANSMITTANCE_SAMPLERS)
            .build())
        .withColorTargetState(lookupTarget())
        .build();

    private PhysicalSkyPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(TRANSMITTANCE, MULTIPLE_SCATTERING, SUN_SKY_VIEW, MOON_SKY_VIEW, SKY, FOG_PASS, HORIZON, SKY_LIGHT);
    }

    private static RenderPipeline.Builder skyView(String name) {
        BindGroupLayout.Builder bindings = BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(SPECTRUM, UniformType.UNIFORM_BUFFER);
        withSamplers(bindings, TRANSMITTANCE_SAMPLERS);
        withSamplers(bindings, MULTIPLE_SCATTERING_SAMPLERS);
        return HelionPipelines.fullscreen(name, SKY_VIEW_SHADER)
            .withBindGroupLayout(bindings.build())
            .withColorTargetState(SKY_VIEW_SCATTERING_TARGET, lookupTarget())
            .withColorTargetState(SKY_VIEW_MIE_TARGET, lookupTarget());
    }

    private static BindGroupLayout.Builder skyColorBindings() {
        return BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(SUN_SKY_VIEW_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(SUN_MIE_VIEW_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(MOON_SKY_VIEW_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(MOON_MIE_VIEW_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER);
    }

    private static BindGroupLayout.Builder withSamplers(BindGroupLayout.Builder bindings, List<String> samplers) {
        samplers.forEach(sampler -> bindings.withUniform(sampler, UniformType.COMBINED_IMAGE_SAMPLER));
        return bindings;
    }

    private static RenderPipeline.Builder spectralTargets(RenderPipeline.Builder builder) {
        for (int group = 0; group < AtmosphereSpectrum.GROUP_COUNT; group++) {
            builder.withColorTargetState(group, lookupTarget());
        }
        return builder;
    }

    private static List<String> samplers(String prefix) {
        return IntStream.range(0, AtmosphereSpectrum.GROUP_COUNT).mapToObj(group -> prefix + group).toList();
    }

    private static ColorTargetState lookupTarget() {
        return new ColorTargetState(Optional.empty(), LOOKUP_FORMAT, ColorTargetState.WRITE_ALL);
    }
}
