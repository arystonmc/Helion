package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongUnaryOperator;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;

final class RtxEntitySurfaces {
    static final int TEXTURE_SLOTS = 16;
    static final long NO_VIEW = 0L;

    private static final String LABEL = "Helion Ray Tracing Entity Surfaces";
    private static final int FRAMES_IN_FLIGHT = 4;
    private static final int CAPACITY = 2048;
    private static final int HEADER_BYTES = 4 * Integer.BYTES;
    private static final int RECORD_BYTES = 12 * Integer.BYTES;
    private static final int NO_SLOT = -1;
    private static final int NO_OFFSET = 0xFF;
    private static final int BYTE_SHIFT = 8;
    private static final int LIGHT_SHIFT = 16;
    private static final int FLAG_SHIFT = 24;
    private static final int SHORT_INDICES = 1;
    private static final int NO_FLAGS = 0;
    private static final int INDEX_ADDRESS = Long.BYTES;
    private static final int BASE_VERTEX = 2 * Long.BYTES;
    private static final int STRIDE = BASE_VERTEX + Integer.BYTES;
    private static final int LAYOUT = STRIDE + Integer.BYTES;
    private static final int SLOT = LAYOUT + Integer.BYTES;
    private static final int INDEX_OFFSET = SLOT + Integer.BYTES;
    private static final int SHADING = INDEX_OFFSET + Integer.BYTES;

    private final RtxBuffer[] tables;
    private final TrackedResource tracked;
    private final long[] views = new long[TEXTURE_SLOTS];
    private final Long2IntOpenHashMap slots = new Long2IntOpenHashMap();
    private int frame;

    private RtxEntitySurfaces(RtxBuffer[] tables, TrackedResource tracked) {
        this.tables = tables;
        this.tracked = tracked;
        slots.defaultReturnValue(NO_SLOT);
    }

    static RtxEntitySurfaces create(RtxDevice device, GpuResources resources) {
        RtxBuffer[] tables = new RtxBuffer[FRAMES_IN_FLIGHT];
        long bytes = 0L;
        for (int slot = 0; slot < FRAMES_IN_FLIGHT; slot++) {
            tables[slot] = RtxBuffer.create(device, HEADER_BYTES + (long) CAPACITY * RECORD_BYTES, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true);
            MemoryUtil.memSet(tables[slot].mapped(), 0, tables[slot].size());
            bytes += tables[slot].size();
        }
        TrackedResource tracked = resources.track(LABEL, bytes, () -> {
            for (RtxBuffer table : tables) {
                table.destroy();
            }
        });
        return new RtxEntitySurfaces(tables, tracked);
    }

    void write(List<RtxEntityDraws.RtxEntityDraw> draws, LongUnaryOperator address) {
        frame = (frame + 1) % FRAMES_IN_FLIGHT;
        Arrays.fill(views, NO_VIEW);
        slots.clear();
        long table = tables[frame].mapped();
        int count = Math.min(draws.size(), CAPACITY);
        MemoryUtil.memPutInt(table, count);
        for (int geometry = 0; geometry < count; geometry++) {
            RtxEntityDraws.RtxEntityDraw draw = draws.get(geometry);
            long record = table + HEADER_BYTES + (long) geometry * RECORD_BYTES;
            MemoryUtil.memPutLong(record, address.applyAsLong(draw.vertexBuffer()));
            MemoryUtil.memPutLong(record + INDEX_ADDRESS, address.applyAsLong(draw.indexBuffer()));
            MemoryUtil.memPutInt(record + BASE_VERTEX, draw.baseVertex());
            MemoryUtil.memPutInt(record + STRIDE, draw.vertexStride());
            MemoryUtil.memPutInt(record + LAYOUT, layout(draw));
            MemoryUtil.memPutInt(record + SLOT, slot(draw));
            MemoryUtil.memPutInt(record + INDEX_OFFSET, draw.indexOffset());
            MemoryUtil.memPutInt(record + SHADING, offsetOrAbsent(draw.normalOffset()) | offsetOrAbsent(draw.overlayOffset()) << BYTE_SHIFT);
        }
    }

    RtxBuffer table() {
        return tables[frame];
    }

    long[] views(long fallback) {
        long[] bound = views.clone();
        for (int slot = 0; slot < bound.length; slot++) {
            if (bound[slot] == NO_VIEW) {
                bound[slot] = fallback;
            }
        }
        return bound;
    }

    void release(GpuResources resources) {
        resources.release(tracked);
    }

    private int slot(RtxEntityDraws.RtxEntityDraw draw) {
        if (draw.textureView() == RtxEntityDraws.RtxEntityDraw.NO_TEXTURE || !readable(draw)) {
            return NO_SLOT;
        }
        int known = slots.get(draw.textureView());
        if (known != NO_SLOT || slots.size() >= TEXTURE_SLOTS) {
            return known;
        }
        int created = slots.size();
        slots.put(draw.textureView(), created);
        views[created] = draw.textureView();
        return created;
    }

    private static boolean readable(RtxEntityDraws.RtxEntityDraw draw) {
        return draw.vertexStride() % Integer.BYTES == 0 && readable(draw.colorOffset()) && readable(draw.uvOffset())
            && (draw.lightOffset() == RtxEntityDraws.Surface.ABSENT || readable(draw.lightOffset()));
    }

    private static boolean readable(int offset) {
        return offset >= 0 && offset < NO_OFFSET && offset % Integer.BYTES == 0;
    }

    private static int offsetOrAbsent(int offset) {
        return readable(offset) ? offset : NO_OFFSET;
    }

    private static int layout(RtxEntityDraws.RtxEntityDraw draw) {
        int light = draw.lightOffset() == RtxEntityDraws.Surface.ABSENT ? NO_OFFSET : draw.lightOffset();
        int flags = draw.indexType() == VK10.VK_INDEX_TYPE_UINT16 ? SHORT_INDICES : NO_FLAGS;
        return (draw.colorOffset() & NO_OFFSET) | (draw.uvOffset() & NO_OFFSET) << BYTE_SHIFT | (light & NO_OFFSET) << LIGHT_SHIFT | flags << FLAG_SHIFT;
    }
}
