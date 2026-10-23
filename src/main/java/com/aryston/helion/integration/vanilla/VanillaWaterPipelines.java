package com.aryston.helion.integration.vanilla;

import com.aryston.helion.Helion;
import com.aryston.helion.render.geometry.GeometryBufferPipelines;
import com.aryston.helion.render.shader.HelionPipelines;
import com.aryston.helion.render.water.WaterPipelines;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class VanillaWaterPipelines {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PIPELINE_PREFIX = "pipeline/water_surface/";
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "water/surface");
    private static final int SURFACE_TARGET = 0;
    private static final int TINT_TARGET = 1;
    private static final VanillaGeometryPipelines.LayerPipelines SURFACE = new VanillaGeometryPipelines.LayerPipelines(
        surface(RenderPipelines.TRANSLUCENT_TERRAIN, "translucent_terrain"),
        surface(RenderPipelines.TRANSLUCENT_TERRAIN_MULTIDRAW, "translucent_terrain_multidraw")
    );
    private static final String GLOSSY_ONLY_DEFINE = "HELION_GLOSSY_ONLY";
    private static final VanillaGeometryPipelines.LayerPipelines GLASS = new VanillaGeometryPipelines.LayerPipelines(
        glass(RenderPipelines.CUTOUT_TERRAIN, "cutout_glass"),
        glass(RenderPipelines.CUTOUT_TERRAIN_MULTIDRAW, "cutout_glass_multidraw")
    );
    private static boolean missingPipelinesReported;

    private VanillaWaterPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(SURFACE.direct(), SURFACE.multiDraw(), GLASS.direct(), GLASS.multiDraw());
    }

    static Optional<VanillaGeometryPipelines.LayerPipelines> surface() {
        boolean compiled = HelionPipelines.compiled(SURFACE.direct()).isPresent() && HelionPipelines.compiled(SURFACE.multiDraw()).isPresent();
        if (!compiled && !missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion water reflections are skipped because the water surface shaders could not be compiled");
        }
        return compiled ? Optional.of(SURFACE) : Optional.empty();
    }

    static Optional<VanillaGeometryPipelines.LayerPipelines> glass() {
        boolean compiled = HelionPipelines.compiled(GLASS.direct()).isPresent() && HelionPipelines.compiled(GLASS.multiDraw()).isPresent();
        return compiled ? Optional.of(GLASS) : Optional.empty();
    }

    private static RenderPipeline glass(RenderPipeline vanilla, String name) {
        return builder(vanilla, name).withShaderDefine(GLOSSY_ONLY_DEFINE).build();
    }

    private static RenderPipeline surface(RenderPipeline vanilla, String name) {
        return builder(vanilla, name).build();
    }

    private static RenderPipeline.Builder builder(RenderPipeline vanilla, String name) {
        return vanilla.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(Helion.MOD_ID, PIPELINE_PREFIX + name))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withColorTargetState(SURFACE_TARGET, new ColorTargetState(Optional.empty(), WaterPipelines.SURFACE_FORMAT, ColorTargetState.WRITE_ALL))
            .withColorTargetState(TINT_TARGET, new ColorTargetState(Optional.empty(), WaterPipelines.TINT_FORMAT, ColorTargetState.WRITE_ALL))
            .withBindGroupLayout(BindGroupLayout.builder()
                .withUniform(WaterPipelines.SURFACE_UNIFORM, UniformType.UNIFORM_BUFFER)
                .withUniform(WaterPipelines.SCENE_DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(GeometryBufferPipelines.GLOSSY_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(WaterPipelines.WAVE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .build());
    }
}
