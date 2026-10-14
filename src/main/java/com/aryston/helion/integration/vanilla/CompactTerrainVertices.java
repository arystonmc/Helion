package com.aryston.helion.integration.vanilla;

import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.slf4j.Logger;

final class CompactTerrainVertices {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFINE = "AERVIUM_COMPACT_VERTICES";
    private static final String SUPPORTED_VERSION = "1";
    private static final int VERTEX_BINDING = 0;
    private static boolean unsupportedReported;

    private CompactTerrainVertices() {
    }

    static RenderPipeline.Builder inherit(RenderPipeline vanilla, RenderPipeline.Builder builder) {
        String version = vanilla.getShaderDefines().values().get(DEFINE);
        if (version == null) {
            return builder;
        }
        return builder.withVertexBinding(VERTEX_BINDING, vanilla.getVertexFormatBinding(VERTEX_BINDING)).withShaderDefine(DEFINE, Integer.parseInt(version));
    }

    static boolean supported() {
        String version = RenderPipelines.SOLID_TERRAIN.getShaderDefines().values().get(DEFINE);
        boolean supported = version == null || SUPPORTED_VERSION.equals(version);
        if (!supported && !unsupportedReported) {
            unsupportedReported = true;
            LOGGER.warn("Helion geometry buffer and shadows are skipped because the terrain uses compact vertex format {} which this Helion version does not know", version);
        }
        return supported;
    }
}
