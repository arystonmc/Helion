package com.aryston.helion.render.rtx;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.vulkan.VK10;

public final class RtxEntityDraws {
    private static final int TRIANGLE_INDICES = 3;
    private static final int SHORT_BYTES = Short.BYTES;
    private static final int INT_BYTES = Integer.BYTES;

    private final List<RtxEntityDraw> draws = new ArrayList<>();
    private boolean capturing;

    public boolean capturing() {
        return capturing;
    }

    public void add(
        GpuBuffer vertices, GpuBuffer indices, boolean shortIndices, int vertexStride, int positionOffset, int baseVertex, int firstIndex, int indexCount, int usedVertices
    ) {
        if (!capturing || indexCount < TRIANGLE_INDICES || !(vertices instanceof VulkanGpuBuffer vertexBuffer) || !(indices instanceof VulkanGpuBuffer indexBuffer)) {
            return;
        }
        int indexBytes = shortIndices ? SHORT_BYTES : INT_BYTES;
        long bufferVertices = vertexBuffer.size() / vertexStride;
        draws.add(new RtxEntityDraw(
            vertexBuffer.vkBuffer(),
            (int) Math.min(bufferVertices, (long) baseVertex + usedVertices),
            indexBuffer.vkBuffer(),
            shortIndices ? VK10.VK_INDEX_TYPE_UINT16 : VK10.VK_INDEX_TYPE_UINT32,
            vertexStride,
            positionOffset,
            baseVertex,
            firstIndex * indexBytes,
            indexCount / TRIANGLE_INDICES
        ));
    }

    void capture(boolean wanted) {
        capturing = wanted;
        draws.clear();
    }

    List<RtxEntityDraw> take() {
        List<RtxEntityDraw> taken = List.copyOf(draws);
        draws.clear();
        return taken;
    }

    record RtxEntityDraw(
        long vertexBuffer,
        int vertexCount,
        long indexBuffer,
        int indexType,
        int vertexStride,
        int positionOffset,
        int baseVertex,
        int indexOffset,
        int triangles
    ) {
    }
}
