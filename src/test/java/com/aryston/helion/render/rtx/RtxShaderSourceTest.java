package com.aryston.helion.render.rtx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class RtxShaderSourceTest {
    private static final String SOURCE = "/assets/helion/shaders/rtx/shadow_mask.csh";

    @Test
    void shadowMaskComputeShaderCompilesWithRayQueries() {
        ByteBuffer spirv = RtxShaderSource.compileCompute(SOURCE, "shadow_mask.csh");
        try {
            assertTrue(spirv.remaining() > 0);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }
}
