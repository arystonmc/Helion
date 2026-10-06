package com.aryston.helion.integration.vanilla;

import com.aryston.helion.Helion;
import com.aryston.helion.render.geometry.GeometryBufferPipelines;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class VanillaGeometryPipelines {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PIPELINE_PREFIX = "pipeline/geometry/";
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "terrain/geometry");
    private static final Map<ChunkSectionLayer, LayerPipelines> BY_LAYER = Map.of(
        ChunkSectionLayer.SOLID, new LayerPipelines(
            geometry(RenderPipelines.SOLID_TERRAIN, "solid_terrain"),
            geometry(RenderPipelines.SOLID_TERRAIN_MULTIDRAW, "solid_terrain_multidraw")
        ),
        ChunkSectionLayer.CUTOUT, new LayerPipelines(
            geometry(RenderPipelines.CUTOUT_TERRAIN, "cutout_terrain"),
            geometry(RenderPipelines.CUTOUT_TERRAIN_MULTIDRAW, "cutout_terrain_multidraw")
        )
    );
    private static boolean missingPipelinesReported;

    private VanillaGeometryPipelines() {
    }

    public static List<RenderPipeline> all() {
        return BY_LAYER.values().stream()
            .flatMap(pipelines -> Stream.of(pipelines.direct(), pipelines.multiDraw()))
            .toList();
    }

    static boolean compiled() {
        boolean compiled = all().stream().allMatch(pipeline -> HelionPipelines.compiled(pipeline).isPresent());
        if (!compiled && !missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion geometry buffer is skipped because its terrain shaders could not be compiled");
        }
        return compiled;
    }

    static LayerPipelines forLayer(ChunkSectionLayer layer) {
        return Objects.requireNonNull(BY_LAYER.get(layer), layer::label);
    }

    private static RenderPipeline geometry(RenderPipeline vanilla, String name) {
        return vanilla.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(Helion.MOD_ID, PIPELINE_PREFIX + name))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withColorTargetState(GeometryBufferPipelines.NORMAL_TARGET, GeometryBufferPipelines.target(GeometryBufferPipelines.NORMAL_FORMAT))
            .withColorTargetState(GeometryBufferPipelines.LIGHT_TARGET, GeometryBufferPipelines.target(GeometryBufferPipelines.LIGHT_FORMAT))
            .withColorTargetState(GeometryBufferPipelines.ALBEDO_TARGET, GeometryBufferPipelines.target(GeometryBufferPipelines.ALBEDO_FORMAT))
            .build();
    }

    record LayerPipelines(RenderPipeline direct, RenderPipeline multiDraw) {
    }
}
