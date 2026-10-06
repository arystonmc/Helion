package com.aryston.helion.integration.vanilla;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

final class SceneFog {
    private GpuBufferSlice current;

    SceneFog(GpuBufferSlice vanilla) {
        this.current = vanilla;
    }

    GpuBufferSlice current() {
        return current;
    }

    void replace(GpuBufferSlice replacement) {
        current = replacement;
    }
}
