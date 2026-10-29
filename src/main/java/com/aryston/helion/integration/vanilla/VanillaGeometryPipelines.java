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
import java.util.stream.Stream;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class VanillaGeometryPipelines {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PIPELINE_PREFIX = "pipeline/geometry/";
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "terrain/geometry");
    private static final Identifier TRANSLUCENT_SHADER = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "terrain/translucent");
    private static final String SEE_THROUGH_GLASS_DEFINE = "HELION_SEE_THROUGH_GLASS";
    private static final Map<ChunkSectionLayer, LayerPipelines> BY_LAYER = layers();
    private static final LayerPipelines SEE_THROUGH_GLASS = new LayerPipelines(
        geometry(RenderPipelines.CUTOUT_TERRAIN, "cutout_terrain_see_through_glass").withShaderDefine(SEE_THROUGH_GLASS_DEFINE).build(),
        geometry(RenderPipelines.CUTOUT_TERRAIN_MULTIDRAW, "cutout_terrain_see_through_glass_multidraw").withShaderDefine(SEE_THROUGH_GLASS_DEFINE).build()
    );
    private static final LayerPipelines SEE_THROUGH_TRANSLUCENT = new LayerPipelines(
        translucent(RenderPipelines.TRANSLUCENT_TERRAIN, "translucent_terrain_see_through_glass"),
        translucent(RenderPipelines.TRANSLUCENT_TERRAIN_MULTIDRAW, "translucent_terrain_see_through_glass_multidraw")
    );
    private static boolean missingPipelinesReported;

    private VanillaGeometryPipelines() {
    }

    public static List<RenderPipeline> all() {
        return Stream.concat(pipelines(), Stream.of(SEE_THROUGH_TRANSLUCENT.direct(), SEE_THROUGH_TRANSLUCENT.multiDraw())).toList();
    }

    static boolean compiled() {
        boolean compiled = CompactTerrainVertices.supported() && pipelines().allMatch(VanillaGeometryPipelines::isCompiled);
        if (!compiled && !missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion geometry buffer is skipped because its terrain shaders could not be compiled");
        }
        return compiled;
    }

    static LayerPipelines forLayer(ChunkSectionLayer layer, boolean seeThroughGlass) {
        if (seeThroughGlass && layer == ChunkSectionLayer.CUTOUT) {
            return SEE_THROUGH_GLASS;
        }
        return Objects.requireNonNull(BY_LAYER.get(layer), layer::label);
    }

    static Optional<LayerPipelines> seeThroughTranslucent() {
        boolean compiled = isCompiled(SEE_THROUGH_TRANSLUCENT.direct()) && isCompiled(SEE_THROUGH_TRANSLUCENT.multiDraw());
        return compiled ? Optional.of(SEE_THROUGH_TRANSLUCENT) : Optional.empty();
    }

    private static Stream<RenderPipeline> pipelines() {
        return Stream.concat(BY_LAYER.values().stream(), Stream.of(SEE_THROUGH_GLASS)).flatMap(pipelines -> Stream.of(pipelines.direct(), pipelines.multiDraw()));
    }

    private static boolean isCompiled(RenderPipeline pipeline) {
        return HelionPipelines.compiled(pipeline).isPresent();
    }

    private static Map<ChunkSectionLayer, LayerPipelines> layers() {
        return Map.of(
            ChunkSectionLayer.SOLID, new LayerPipelines(
                geometry(RenderPipelines.SOLID_TERRAIN, "solid_terrain").build(),
                geometry(RenderPipelines.SOLID_TERRAIN_MULTIDRAW, "solid_terrain_multidraw").build()
            ),
            ChunkSectionLayer.CUTOUT, new LayerPipelines(
                geometry(RenderPipelines.CUTOUT_TERRAIN, "cutout_terrain").build(),
                geometry(RenderPipelines.CUTOUT_TERRAIN_MULTIDRAW, "cutout_terrain_multidraw").build()
            )
        );
    }

    private static RenderPipeline.Builder geometry(RenderPipeline vanilla, String name) {
        return vanilla.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(Helion.MOD_ID, PIPELINE_PREFIX + name))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withColorTargetState(GeometryBufferPipelines.NORMAL_TARGET, GeometryBufferPipelines.target(GeometryBufferPipelines.NORMAL_FORMAT))
            .withColorTargetState(GeometryBufferPipelines.LIGHT_TARGET, GeometryBufferPipelines.target(GeometryBufferPipelines.LIGHT_FORMAT))
            .withColorTargetState(GeometryBufferPipelines.ALBEDO_TARGET, GeometryBufferPipelines.target(GeometryBufferPipelines.ALBEDO_FORMAT))
            .withBindGroupLayout(BindGroupLayout.builder()
                .withUniform(GeometryBufferPipelines.GLOSSY_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .build());
    }

    private static RenderPipeline translucent(RenderPipeline vanilla, String name) {
        return vanilla.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(Helion.MOD_ID, PIPELINE_PREFIX + name))
            .withFragmentShader(TRANSLUCENT_SHADER)
            .withBindGroupLayout(BindGroupLayout.builder()
                .withUniform(GeometryBufferPipelines.GLOSSY_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .build())
            .build();
    }

    record LayerPipelines(RenderPipeline direct, RenderPipeline multiDraw) {
    }
}
