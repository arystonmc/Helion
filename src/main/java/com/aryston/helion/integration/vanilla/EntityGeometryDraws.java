package com.aryston.helion.integration.vanilla;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import org.jspecify.annotations.Nullable;

public final class EntityGeometryDraws {
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
