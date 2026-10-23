package com.aryston.helion.integration.vanilla;

import com.aryston.helion.Helion;
import com.aryston.helion.render.geometry.GeometryBufferPipelines;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class VanillaGeometryPipelines {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PIPELINE_PREFIX = "pipeline/geometry/";
    private static final String DYNAMIC_LIGHT_SUFFIX = "_dynamic_light";
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "terrain/geometry");
    private static final Map<ChunkSectionLayer, LayerPipelines> BY_LAYER = layers("", UnaryOperator.identity());
    private static final Map<ChunkSectionLayer, LayerPipelines> WITH_DYNAMIC_LIGHTS = layers(
        DYNAMIC_LIGHT_SUFFIX, VanillaDynamicLightPipelines::withLights
    );
    private static boolean missingPipelinesReported;

    private VanillaGeometryPipelines() {
    }

    public static List<RenderPipeline> all() {
        return Stream.concat(pipelines(BY_LAYER), pipelines(WITH_DYNAMIC_LIGHTS)).toList();
    }

    static boolean compiled() {
        boolean compiled = CompactTerrainVertices.supported() && pipelines(BY_LAYER).allMatch(VanillaGeometryPipelines::isCompiled);
        if (!compiled && !missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion geometry buffer is skipped because its terrain shaders could not be compiled");
        }
        return compiled;
    }

    static LayerPipelines forLayer(ChunkSectionLayer layer) {
        return Objects.requireNonNull(BY_LAYER.get(layer), layer::label);
    }

    static Optional<LayerPipelines> withDynamicLights(ChunkSectionLayer layer) {
        LayerPipelines pipelines = Objects.requireNonNull(WITH_DYNAMIC_LIGHTS.get(layer), layer::label);
        return isCompiled(pipelines.direct()) && isCompiled(pipelines.multiDraw()) ? Optional.of(pipelines) : Optional.empty();
    }

    private static Stream<RenderPipeline> pipelines(Map<ChunkSectionLayer, LayerPipelines> layers) {
        return layers.values().stream().flatMap(pipelines -> Stream.of(pipelines.direct(), pipelines.multiDraw()));
    }

    private static boolean isCompiled(RenderPipeline pipeline) {
        return HelionPipelines.compiled(pipeline).isPresent();
    }

    private static Map<ChunkSectionLayer, LayerPipelines> layers(String suffix, UnaryOperator<RenderPipeline.Builder> variant) {
        return Map.of(
            ChunkSectionLayer.SOLID, new LayerPipelines(
                geometry(RenderPipelines.SOLID_TERRAIN, "solid_terrain" + suffix, variant),
                geometry(RenderPipelines.SOLID_TERRAIN_MULTIDRAW, "solid_terrain_multidraw" + suffix, variant)
            ),
            ChunkSectionLayer.CUTOUT, new LayerPipelines(
                geometry(RenderPipelines.CUTOUT_TERRAIN, "cutout_terrain" + suffix, variant),
                geometry(RenderPipelines.CUTOUT_TERRAIN_MULTIDRAW, "cutout_terrain_multidraw" + suffix, variant)
            )
        );
    }

    private static RenderPipeline geometry(RenderPipeline vanilla, String name, UnaryOperator<RenderPipeline.Builder> variant) {
        return variant.apply(vanilla.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(Helion.MOD_ID, PIPELINE_PREFIX + name))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withColorTargetState(GeometryBufferPipelines.NORMAL_TARGET, GeometryBufferPipelines.target(GeometryBufferPipelines.NORMAL_FORMAT))
            .withColorTargetState(GeometryBufferPipelines.LIGHT_TARGET, GeometryBufferPipelines.target(GeometryBufferPipelines.LIGHT_FORMAT))
            .withColorTargetState(GeometryBufferPipelines.ALBEDO_TARGET, GeometryBufferPipelines.target(GeometryBufferPipelines.ALBEDO_FORMAT))
            .withBindGroupLayout(BindGroupLayout.builder()
                .withUniform(GeometryBufferPipelines.GLOSSY_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .build()))
            .build();
    }

    record LayerPipelines(RenderPipeline direct, RenderPipeline multiDraw) {
    }
}
