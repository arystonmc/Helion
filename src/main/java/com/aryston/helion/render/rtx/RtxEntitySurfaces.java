package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.List;
import java.util.function.LongUnaryOperator;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;

final class RtxEntitySurfaces {
    static final int CAPACITY = 4096;

    private static final String LABEL = "Helion Ray Tracing Entity Surfaces";
    private static final int FRAMES_IN_FLIGHT = 4;
    private static final int USAGE = VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
    private static final int RECORD_BYTES = 8 * Integer.BYTES;
    private static final int INDEX_ADDRESS = Long.BYTES;
    private static final int INDEX_OFFSET = 2 * Long.BYTES;
    private static final int BASE_VERTEX = INDEX_OFFSET + Integer.BYTES;
    private static final int LAYOUT = BASE_VERTEX + Integer.BYTES;
    private static final int MATERIAL = LAYOUT + Integer.BYTES;
    private static final int NO_TEXTURE = 0xFFFF;
    private static final int SHORT_INDICES = 1 << 16;
    private static final int CUTOUT_SHIFT = 24;
    private static final int COLOR_SHIFT = 8;
    private static final int UV_SHIFT = 16;
    private static final int LIGHT_SHIFT = 24;
    private static final int BYTE_LIMIT = 0xFF;
    private static final float CUTOUT_SCALE = 255.0F;
    private static final int UNREADABLE = 0;

    private final RtxBuffer[] tables;
    private final Long2IntOpenHashMap slots = new Long2IntOpenHashMap();
    private final long[] textures = new long[RtxEntityTextures.CAPACITY];
    private int frame;
    private int count;

    private RtxEntitySurfaces(RtxBuffer[] tables) {
        this.tables = tables;
        slots.defaultReturnValue(NO_TEXTURE);
    }

    static RtxEntitySurfaces create(RtxDevice device, GpuResources resources) {
        RtxBuffer[] tables = new RtxBuffer[FRAMES_IN_FLIGHT];
        for (int slot = 0; slot < FRAMES_IN_FLIGHT; slot++) {
            tables[slot] = RtxBuffer.create(device, (long) CAPACITY * RECORD_BYTES, USAGE, true);
        }
        resources.track(LABEL, (long) FRAMES_IN_FLIGHT * CAPACITY * RECORD_BYTES, () -> {
            for (RtxBuffer table : tables) {
                table.destroy();
            }
        });
        return new RtxEntitySurfaces(tables);
    }

    void write(List<RtxEntityDraws.RtxEntityDraw> draws, LongUnaryOperator address) {
        frame = (frame + 1) % FRAMES_IN_FLIGHT;
        slots.clear();
        long base = tables[frame].mapped();
        count = Math.min(draws.size(), CAPACITY);
        for (int index = 0; index < count; index++) {
            RtxEntityDraws.RtxEntityDraw draw = draws.get(index);
            long entry = base + (long) index * RECORD_BYTES;
            boolean readable = readable(draw);
            MemoryUtil.memPutLong(entry, address.applyAsLong(draw.vertexBuffer()));
            MemoryUtil.memPutLong(entry + INDEX_ADDRESS, address.applyAsLong(draw.indexBuffer()));
            MemoryUtil.memPutInt(entry + INDEX_OFFSET, draw.indexOffset());
            MemoryUtil.memPutInt(entry + BASE_VERTEX, draw.baseVertex());
            MemoryUtil.memPutInt(entry + LAYOUT, readable ? layout(draw) : UNREADABLE);
            MemoryUtil.memPutInt(entry + MATERIAL, material(draw, readable));
        }
    }

    RtxBuffer table() {
        return tables[frame];
    }

    int count() {
        return count;
    }

    long[] textures() {
        return textures;
    }

    int textureCount() {
        return slots.size();
    }

    private int material(RtxEntityDraws.RtxEntityDraw draw, boolean readable) {
        int slot = readable && draw.hasTexture() ? slot(draw.textureView()) : NO_TEXTURE;
        int shortIndices = draw.indexType() == VK10.VK_INDEX_TYPE_UINT16 ? SHORT_INDICES : 0;
        int cutout = Math.clamp(Math.round(draw.cutout() * CUTOUT_SCALE), 0, BYTE_LIMIT);
        return slot | shortIndices | cutout << CUTOUT_SHIFT;
    }

    private int slot(long textureView) {
        int known = slots.get(textureView);
        if (known != NO_TEXTURE || slots.size() == textures.length) {
            return known;
        }
        int slot = slots.size();
        slots.put(textureView, slot);
        textures[slot] = textureView;
        return slot;
    }

    private static int layout(RtxEntityDraws.RtxEntityDraw draw) {
        return draw.vertexStride() | draw.colorOffset() << COLOR_SHIFT | draw.uvOffset() << UV_SHIFT | draw.lightOffset() << LIGHT_SHIFT;
    }

    private static boolean readable(RtxEntityDraws.RtxEntityDraw draw) {
        return wordAligned(draw.vertexStride()) && wordAligned(draw.colorOffset()) && wordAligned(draw.uvOffset()) && wordAligned(draw.lightOffset());
    }

    private static boolean wordAligned(int bytes) {
        return bytes >= 0 && bytes <= BYTE_LIMIT && bytes % Integer.BYTES == 0;
    }
}
