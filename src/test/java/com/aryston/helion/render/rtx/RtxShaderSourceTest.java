package com.aryston.helion.render.rtx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.system.MemoryUtil;

class RtxShaderSourceTest {
    private static final String SHADER_ROOT = "/assets/helion/shaders/rtx/";
    private static final String REFLECTION = "reflection.csh";
    private static final String PATH_TRACE = "path_trace.csh";
    private static final String ACCUMULATE = "denoise_accumulate.csh";
    private static final String FILTER = "denoise_filter.csh";
    private static final String SPECULAR = "specular_accumulate.csh";
    private static final String UPSAMPLE = "denoise_upsample.csh";
    private static final String ENTITY_REFLECTION_DEFINE = "HELION_RT_ENTITIES";
    private static final String LIGHT_CACHE_UPDATE_DEFINE = "HELION_LIGHT_CACHE_UPDATE";

    @ParameterizedTest
    @ValueSource(strings = {REFLECTION, PATH_TRACE, ACCUMULATE, FILTER, SPECULAR, UPSAMPLE})
    void computeShaderCompilesWithRayQueries(String shader) {
        assertCompiles(shader, Set.of());
    }

    @Test
    void reflectionCompilesWithEntities() {
        assertCompiles(REFLECTION, Set.of(ENTITY_REFLECTION_DEFINE));
    }

    @ParameterizedTest
    @EnumSource(PathTracingQuality.class)
    void pathTraceCompilesEveryQuality(PathTracingQuality quality) {
        assertCompiles(PATH_TRACE, Set.of(quality.define()));
        assertCompiles(PATH_TRACE, Set.of(quality.define(), LIGHT_CACHE_UPDATE_DEFINE));
    }

    private static void assertCompiles(String shader, Set<String> defines) {
        ByteBuffer spirv = RtxShaderSource.compileCompute(SHADER_ROOT + shader, shader, defines);
        try {
            assertTrue(spirv.remaining() > 0);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }
}
