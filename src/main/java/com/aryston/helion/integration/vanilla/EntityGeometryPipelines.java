package com.aryston.helion.integration.vanilla;

import com.aryston.helion.Helion;
import com.aryston.helion.render.geometry.GeometryBufferPipelines;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class EntityGeometryPipelines {
    private static final String PIPELINE_PREFIX = "pipeline/entity_geometry/";
    private static final String PATH_SEPARATOR = "/";
    private static final String NAME_SEPARATOR = "_";
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "entity/geometry");
    private static final Identifier ENTITY_SHADER = Identifier.withDefaultNamespace("core/entity");
    private static final Identifier ITEM_SHADER = Identifier.withDefaultNamespace("core/item");
    private static final String ITEM_LAYOUT_DEFINE = "HELION_ITEM_LAYOUT";
    private static final String LIGHTMAP_SAMPLER = "Sampler2";
    private static final String OVERLAY_SAMPLER = "Sampler1";
    private static final String NO_OVERLAY_DEFINE = "NO_OVERLAY";
    private static final int SCENE_TARGET = 0;
    private static final Set<String> UNSUPPORTED_DEFINES = Set.of(
        "EMISSIVE", "GLINT", "GLINT_SPECIAL", "DISSOLVE", "OIT_ALPHA_ONLY", "OIT_ACCUMULATE", "OIT_ADDITIVE"
    );
    private static final Map<RenderPipeline, RenderPipeline> VARIANTS = variants();

    private EntityGeometryPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.copyOf(VARIANTS.values());
    }

    static @Nullable RenderPipeline variant(RenderPipeline vanilla) {
        return VARIANTS.get(vanilla);
    }

    private static Map<RenderPipeline, RenderPipeline> variants() {
        Map<RenderPipeline, RenderPipeline> variants = new IdentityHashMap<>();
        Stream.concat(RenderPipelines.requiredPipelines().stream(), RenderPipelines.optionalPipelines().stream())
            .filter(EntityGeometryPipelines::supported)
            .forEach(vanilla -> variants.put(vanilla, geometry(vanilla)));
        return variants;
    }

    private static boolean supported(RenderPipeline pipeline) {
        Identifier vertex = pipeline.getShaders().get(ShaderType.VERTEX);
        Identifier fragment = pipeline.getShaders().get(ShaderType.FRAGMENT);
        if (vertex == null || !vertex.equals(fragment) || !(vertex.equals(ENTITY_SHADER) || vertex.equals(ITEM_SHADER))) {
            return false;
        }
        ShaderDefines defines = pipeline.getShaderDefines();
        if (UNSUPPORTED_DEFINES.stream().anyMatch(name -> defines.flags().contains(name) || defines.values().containsKey(name))) {
            return false;
        }
        List<@Nullable ColorTargetState> targets = pipeline.getColorTargetStates();
        ColorTargetState scene = targets.isEmpty() ? null : targets.get(SCENE_TARGET);
        DepthStencilState depth = pipeline.getDepthStencilState();
        if (targets.size() != 1 || scene == null || scene.blendFunction().isPresent() || depth == null || !depth.writeDepth()) {
            return false;
        }
        Set<String> uniforms = uniformNames(pipeline);
        boolean overlay = defines.flags().contains(NO_OVERLAY_DEFINE) || uniforms.contains(OVERLAY_SAMPLER);
        return uniforms.contains(LIGHTMAP_SAMPLER) && overlay;
    }

    private static Set<String> uniformNames(RenderPipeline pipeline) {
        return BindGroupLayout.flattenUniforms(pipeline.getBindGroupLayouts()).stream()
            .map(BindGroupLayout.UniformDescription::name)
            .collect(Collectors.toSet());
    }

    private static RenderPipeline geometry(RenderPipeline vanilla) {
        RenderPipeline.Builder builder = vanilla.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(Helion.MOD_ID, PIPELINE_PREFIX + name(vanilla)))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withColorTargetState(GeometryBufferPipelines.NORMAL_TARGET, GeometryBufferPipelines.target(GeometryBufferPipelines.NORMAL_FORMAT))
            .withColorTargetState(GeometryBufferPipelines.LIGHT_TARGET, GeometryBufferPipelines.target(GeometryBufferPipelines.LIGHT_FORMAT))
            .withColorTargetState(GeometryBufferPipelines.ALBEDO_TARGET, GeometryBufferPipelines.target(GeometryBufferPipelines.ALBEDO_FORMAT));
        if (ITEM_SHADER.equals(vanilla.getShaders().get(ShaderType.VERTEX))) {
            builder.withShaderDefine(ITEM_LAYOUT_DEFINE);
        }
        return builder.build();
    }

    private static String name(RenderPipeline vanilla) {
        Identifier location = vanilla.getLocation();
        return location.getNamespace() + NAME_SEPARATOR + location.getPath().replace(PATH_SEPARATOR, NAME_SEPARATOR);
    }

    static boolean compiled(RenderPipeline variant) {
        return HelionPipelines.compiled(variant).isPresent();
    }

    static boolean anyCompiled() {
        return VARIANTS.values().stream().anyMatch(EntityGeometryPipelines::compiled);
    }
}
