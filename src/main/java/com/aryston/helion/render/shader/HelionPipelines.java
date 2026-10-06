package com.aryston.helion.render.shader;

import com.aryston.helion.Helion;
import com.aryston.helion.render.lighting.AmbientOcclusionPipelines;
import com.aryston.helion.render.post.ImagePipelines;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;

public final class HelionPipelines {
    private static final Identifier SCREEN_QUAD_VERTEX_SHADER = Identifier.withDefaultNamespace("core/screenquad");
    private static final String PIPELINE_PREFIX = "pipeline/";

    private HelionPipelines() {
    }

    public static List<RenderPipeline> all() {
        return Stream.concat(AmbientOcclusionPipelines.all().stream(), ImagePipelines.all().stream()).toList();
    }

    public static RenderPipeline.Builder fullscreen(String fragmentShader) {
        return fullscreen(fragmentShader, fragmentShader);
    }

    public static RenderPipeline.Builder fullscreen(String name, String fragmentShader) {
        return RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(Helion.MOD_ID, PIPELINE_PREFIX + name))
            .withVertexShader(SCREEN_QUAD_VERTEX_SHADER)
            .withFragmentShader(Identifier.fromNamespaceAndPath(Helion.MOD_ID, fragmentShader))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
    }

    public static Optional<CompiledRenderPipeline> compiled(RenderPipeline pipeline) {
        return Optional.ofNullable(RenderSystem.getCompiledPipelineNullable(pipeline));
    }
}
