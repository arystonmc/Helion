package com.aryston.helion.render.geometry;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class GeometryBufferPipelines {
    public static final int SCENE_COLOR_TARGET = 0;
    public static final int NORMAL_TARGET = 1;
    public static final int LIGHT_TARGET = 2;
    public static final int ALBEDO_TARGET = 3;
    public static final GpuFormat NORMAL_FORMAT = GpuFormat.RGB10A2_UNORM;
    public static final GpuFormat LIGHT_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final GpuFormat ALBEDO_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final GpuFormat OUTPUT_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final String NORMAL_SAMPLER = "NormalSampler";
    public static final String LIGHT_SAMPLER = "LightSampler";
    public static final String ALBEDO_SAMPLER = "AlbedoSampler";
    private static final String VIEW_DEFINE = "HELION_GEOMETRY_VIEW";
    private static final String DEBUG_SHADER = "geometry/debug";
    private static final String DEBUG_PIPELINE = "geometry/debug_";

    private static final Map<GeometryBufferView, RenderPipeline> DEBUG_VIEWS = debugViews();

    private GeometryBufferPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.copyOf(DEBUG_VIEWS.values());
    }

    public static ColorTargetState target(GpuFormat format) {
        return new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL);
    }

    static Optional<RenderPipeline> debugView(GeometryBufferView view) {
        return Optional.ofNullable(DEBUG_VIEWS.get(view));
    }

    private static Map<GeometryBufferView, RenderPipeline> debugViews() {
        Map<GeometryBufferView, RenderPipeline> pipelines = new EnumMap<>(GeometryBufferView.class);
        Arrays.stream(GeometryBufferView.values())
            .filter(view -> view != GeometryBufferView.NONE)
            .forEach(view -> pipelines.put(view, HelionPipelines.fullscreen(DEBUG_PIPELINE + view.name().toLowerCase(Locale.ROOT), DEBUG_SHADER)
                .withShaderDefine(VIEW_DEFINE, view.shaderId())
                .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform(NORMAL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform(LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform(ALBEDO_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                    .build())
                .withColorTargetState(target(OUTPUT_FORMAT))
                .build()));
        return pipelines;
    }
}
