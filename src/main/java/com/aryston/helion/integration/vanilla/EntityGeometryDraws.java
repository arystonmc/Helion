package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.rtx.RtxEntityDraws;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import org.jspecify.annotations.Nullable;

public final class EntityGeometryDraws {
    private static final int VERTEX_BINDING = 0;
    private static final String POSITION = "Position";
    private static final int QUAD_INDICES = 6;
    private static final int QUAD_VERTICES = 4;
    private static Mode mode = Mode.ALL;

    private EntityGeometryDraws() {
    }

    static void run(Mode drawn, Runnable draws) {
        mode = drawn;
        try {
            draws.run();
        } finally {
            mode = Mode.ALL;
        }
    }

    public static boolean skipsGroup(FeatureRenderer<?> renderer) {
        return mode == Mode.GEOMETRY_ONLY && !(renderer instanceof RenderTypeFeatureRenderer<?>);
    }

    public static boolean skipsDraw(RenderPipeline pipeline) {
        return switch (mode) {
            case ALL -> false;
            case GEOMETRY_ONLY -> compiledVariant(pipeline) == null;
            case REMAINDER_ONLY -> compiledVariant(pipeline) != null;
        };
    }

    public static void drawn(StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
        if (mode != Mode.GEOMETRY_ONLY || (info.topology() != PrimitiveTopology.QUADS && info.topology() != PrimitiveTopology.TRIANGLES)) {
            return;
        }
        RtxEntityDraws draws = HelionRenderCore.get().rayTracing().entityDraws();
        VertexFormat format = pipeline.getVertexFormatBinding(VERTEX_BINDING);
        VertexFormatElement position = format == null ? null : format.getElement(POSITION);
        if (!draws.capturing() || format == null || position == null || position.format() != GpuFormat.RGB32_FLOAT) {
            return;
        }
        int usedVertices = info.customIndexBuffer() != null
            ? Integer.MAX_VALUE
            : info.topology() == PrimitiveTopology.QUADS ? info.indexCount() / QUAD_INDICES * QUAD_VERTICES : info.indexCount();
        draws.add(
            info.vertexBuffer(), info.indexBuffer(), info.indexType() == IndexType.SHORT, format.getVertexSize(), position.offset(),
            info.baseVertex(), info.firstIndex(), info.indexCount(), usedVertices
        );
    }

    public static RenderPipeline substitute(RenderPipeline pipeline) {
        if (mode != Mode.GEOMETRY_ONLY) {
            return pipeline;
        }
        RenderPipeline variant = compiledVariant(pipeline);
        return variant == null ? pipeline : variant;
    }

    private static @Nullable RenderPipeline compiledVariant(RenderPipeline pipeline) {
        RenderPipeline variant = EntityGeometryPipelines.variant(pipeline);
        return variant != null && EntityGeometryPipelines.compiled(variant) ? variant : null;
    }

    enum Mode {
        ALL,
        GEOMETRY_ONLY,
        REMAINDER_ONLY
    }
}
