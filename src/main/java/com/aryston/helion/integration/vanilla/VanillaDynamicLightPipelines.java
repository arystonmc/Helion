package com.aryston.helion.integration.vanilla;

import com.aryston.helion.Helion;
import com.aryston.helion.render.lighting.DynamicLightResources;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class VanillaDynamicLightPipelines {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PIPELINE_PREFIX = "pipeline/dynamic_light/";
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "terrain/dynamic_light");
    private static final Map<ChunkSectionLayer, VanillaGeometryPipelines.LayerPipelines> BY_LAYER = Map.of(
        ChunkSectionLayer.SOLID, new VanillaGeometryPipelines.LayerPipelines(
            dynamic(RenderPipelines.SOLID_TERRAIN, "solid_terrain"),
            dynamic(RenderPipelines.SOLID_TERRAIN_MULTIDRAW, "solid_terrain_multidraw")
        ),
        ChunkSectionLayer.CUTOUT, new VanillaGeometryPipelines.LayerPipelines(
            dynamic(RenderPipelines.CUTOUT_TERRAIN, "cutout_terrain"),
            dynamic(RenderPipelines.CUTOUT_TERRAIN_MULTIDRAW, "cutout_terrain_multidraw")
        ),
        ChunkSectionLayer.TRANSLUCENT, new VanillaGeometryPipelines.LayerPipelines(
            dynamic(RenderPipelines.TRANSLUCENT_TERRAIN, "translucent_terrain"),
            dynamic(RenderPipelines.TRANSLUCENT_TERRAIN_MULTIDRAW, "translucent_terrain_multidraw")
        )
    );
    private static boolean missingPipelinesReported;

    private VanillaDynamicLightPipelines() {
    }

    public static List<RenderPipeline> all() {
        return BY_LAYER.values().stream()
            .flatMap(pipelines -> Stream.of(pipelines.direct(), pipelines.multiDraw()))
            .toList();
    }

    static Optional<VanillaGeometryPipelines.LayerPipelines> forLayer(ChunkSectionLayer layer) {
        VanillaGeometryPipelines.LayerPipelines pipelines = BY_LAYER.get(layer);
        if (pipelines == null || !compiled(pipelines)) {
            return Optional.empty();
        }
        return Optional.of(pipelines);
    }

    static RenderPipeline.Builder withLights(RenderPipeline.Builder builder) {
        return builder
            .withShaderDefine(DynamicLightResources.DEFINE)
            .withShaderDefine(DynamicLightResources.MAX_LIGHTS_DEFINE, DynamicLightResources.MAX_LIGHTS)
            .withBindGroupLayout(BindGroupLayout.builder()
                .withUniform(DynamicLightResources.UNIFORM, UniformType.UNIFORM_BUFFER)
                .build());
    }

    private static boolean compiled(VanillaGeometryPipelines.LayerPipelines pipelines) {
        boolean compiled = HelionPipelines.compiled(pipelines.direct()).isPresent() && HelionPipelines.compiled(pipelines.multiDraw()).isPresent();
        if (!compiled && !missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion dynamic lights are skipped on some terrain because their shaders could not be compiled");
        }
        return compiled;
    }

    private static RenderPipeline dynamic(RenderPipeline vanilla, String name) {
        return withLights(vanilla.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(Helion.MOD_ID, PIPELINE_PREFIX + name))
            .withVertexShader(VERTEX_SHADER))
            .build();
    }
}
