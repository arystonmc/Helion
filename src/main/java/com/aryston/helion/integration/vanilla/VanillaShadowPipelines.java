package com.aryston.helion.integration.vanilla;

import com.aryston.helion.Helion;
import com.aryston.helion.render.shader.HelionPipelines;
import com.aryston.helion.render.shadow.ShadowPipelines;
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

public final class VanillaShadowPipelines {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PIPELINE_PREFIX = "pipeline/shadow/";
    private static final String ALPHA_CUTOUT = "ALPHA_CUTOUT";
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "shadow/caster");
    private static final Map<ChunkSectionLayer, VanillaGeometryPipelines.LayerPipelines> BY_LAYER = Map.of(
        ChunkSectionLayer.SOLID, new VanillaGeometryPipelines.LayerPipelines(
            caster(RenderPipelines.TERRAIN_SNIPPET, RenderPipelines.SOLID_TERRAIN, "solid_terrain"),
            caster(RenderPipelines.MULTIDRAW_TERRAIN_SNIPPET, RenderPipelines.SOLID_TERRAIN_MULTIDRAW, "solid_terrain_multidraw")
        ),
        ChunkSectionLayer.CUTOUT, new VanillaGeometryPipelines.LayerPipelines(
            caster(RenderPipelines.TERRAIN_SNIPPET, RenderPipelines.CUTOUT_TERRAIN, "cutout_terrain"),
            caster(RenderPipelines.MULTIDRAW_TERRAIN_SNIPPET, RenderPipelines.CUTOUT_TERRAIN_MULTIDRAW, "cutout_terrain_multidraw")
        )
    );
    private static boolean missingPipelinesReported;

    private VanillaShadowPipelines() {
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
            LOGGER.warn("Helion shadows are skipped because their caster shaders could not be compiled");
        }
        return compiled;
    }

    static VanillaGeometryPipelines.LayerPipelines forLayer(ChunkSectionLayer layer) {
        return Objects.requireNonNull(BY_LAYER.get(layer), layer::label);
    }

    private static RenderPipeline caster(RenderPipeline.Snippet terrain, RenderPipeline vanilla, String name) {
        RenderPipeline.Builder builder = RenderPipeline.builder(terrain)
            .withLocation(Identifier.fromNamespaceAndPath(Helion.MOD_ID, PIPELINE_PREFIX + name))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withDepthStencilState(ShadowPipelines.CASTER_DEPTH);
        String alphaCutout = vanilla.getShaderDefines().values().get(ALPHA_CUTOUT);
        if (alphaCutout != null) {
            builder.withShaderDefine(ALPHA_CUTOUT, Float.parseFloat(alphaCutout));
        }
        return builder.build();
    }
}
