package com.aryston.helion.render.rtx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.system.MemoryUtil;

class RtxShaderSourceTest {
    private static final String SHADER_ROOT = "/assets/helion/shaders/rtx/";
    private static final String REFLECTION = "reflection.csh";

    @ParameterizedTest
    @ValueSource(strings = {"shadow_mask.csh", "sun_shadow.csh", "reflection.csh"})
    void computeShaderCompilesWithRayQueries(String shader) {
        ByteBuffer spirv = RtxShaderSource.compileCompute(SHADER_ROOT + shader, shader);
        try {
            assertTrue(spirv.remaining() > 0);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void reflectionCompilesWithEntityTextures() {
        ByteBuffer spirv = RtxShaderSource.compileCompute(SHADER_ROOT + REFLECTION, REFLECTION, List.of(RtxEntityTextures.DEFINE));
        try {
            assertTrue(spirv.remaining() > 0);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }
}
