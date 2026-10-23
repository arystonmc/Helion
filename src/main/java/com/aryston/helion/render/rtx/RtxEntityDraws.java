package com.aryston.helion.render.rtx;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuBuffer;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
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
        GpuBuffer vertices, GpuBuffer indices, boolean shortIndices, int vertexStride, int positionOffset, int baseVertex, int firstIndex, int indexCount,
        int usedVertices, Surface surface
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
            indexCount / TRIANGLE_INDICES,
            surface.texture() instanceof VulkanGpuTextureView view ? view.vkImageView() : RtxEntityDraw.NO_TEXTURE,
            surface.colorOffset(),
            surface.uvOffset(),
            surface.lightOffset()
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

    public record Surface(@Nullable GpuTextureView texture, int colorOffset, int uvOffset, int lightOffset) {
        public static final int NO_LIGHT = -1;
        public static final Surface NONE = new Surface(null, NO_LIGHT, NO_LIGHT, NO_LIGHT);
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
        int triangles,
        long textureView,
        int colorOffset,
        int uvOffset,
        int lightOffset
    ) {
        static final long NO_TEXTURE = 0L;
    }
}
