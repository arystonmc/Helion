package com.aryston.helion.render.voxel;

import com.aryston.helion.render.resource.GpuResources;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

final class VoxelLightTextures {
    static final int LIGHT_ROWS = 2;
    static final int GRID_WIDTH = VoxelLightGrid.CELLS_PER_AXIS * VoxelLightGrid.LIGHTS_PER_CELL;
    static final int GRID_HEIGHT = VoxelLightGrid.CELL_COUNT / VoxelLightGrid.CELLS_PER_AXIS;

    private static final int FLOAT_BYTES = Float.BYTES;
    private static final int TEXEL_FLOATS = 4;
    private static final int LIGHT_BYTES = VoxelLightGrid.MAX_LIGHTS * LIGHT_ROWS * TEXEL_FLOATS * FLOAT_BYTES;
    private static final int GRID_BYTES = GRID_WIDTH * GRID_HEIGHT * Short.BYTES;
    private static final float BLOCK_CENTER = 0.5F;
    private static final float STEADY = 0.0F;
    private static final float FLICKERING = 1.0F;

    private final VoxelTexture lights = new VoxelTexture("Helion Voxel Lights", GpuFormat.RGBA32_FLOAT, VoxelLightGrid.MAX_LIGHTS, LIGHT_ROWS);
    private final VoxelTexture grid = new VoxelTexture("Helion Voxel Light Grid", GpuFormat.R16_UNORM, GRID_WIDTH, GRID_HEIGHT);
    private @Nullable ByteBuffer lightData;
    private @Nullable ByteBuffer gridData;
    private int lightCount;

    void ensureAllocated(GpuResources resources) {
        if (lights.allocated()) {
            return;
        }
        lights.allocate(resources);
        grid.allocate(resources);
        lightData = MemoryUtil.memCalloc(LIGHT_BYTES);
        gridData = MemoryUtil.memCalloc(GRID_BYTES);
        lights.writeAll(lightData);
        grid.writeAll(gridData);
    }

    void upload(VoxelLightGrid built, int minX, int minY, int minZ) {
        ByteBuffer data = Objects.requireNonNull(lightData);
        List<VoxelLight> list = built.lights();
        int colorRow = VoxelLightGrid.MAX_LIGHTS * TEXEL_FLOATS;
        for (int index = 0; index < list.size(); index++) {
            VoxelLight light = list.get(index);
            int position = index * TEXEL_FLOATS;
            putTexel(data, position, light.x() - minX + BLOCK_CENTER, light.y() - minY + BLOCK_CENTER, light.z() - minZ + BLOCK_CENTER, light.level());
            putTexel(data, colorRow + position, light.red(), light.green(), light.blue(), light.flickers() ? FLICKERING : STEADY);
        }
        lightCount = list.size();
        ByteBuffer cells = Objects.requireNonNull(gridData);
        short[] values = built.cells();
        for (int cell = 0; cell < VoxelLightGrid.CELL_COUNT; cell++) {
            int texelX = cell % VoxelLightGrid.CELLS_PER_AXIS * VoxelLightGrid.LIGHTS_PER_CELL;
            int texelY = cell / VoxelLightGrid.CELLS_PER_AXIS;
            for (int slot = 0; slot < VoxelLightGrid.LIGHTS_PER_CELL; slot++) {
                cells.putShort(((texelY * GRID_WIDTH) + texelX + slot) * Short.BYTES, values[cell * VoxelLightGrid.LIGHTS_PER_CELL + slot]);
            }
        }
        lights.writeAll(data);
        grid.writeAll(cells);
    }

    int lightCount() {
        return lightCount;
    }

    GpuTextureView lightView() {
        return lights.view();
    }

    GpuTextureView gridView() {
        return grid.view();
    }

    void close(GpuResources resources) {
        lights.close(resources);
        grid.close(resources);
        if (lightData != null) {
            MemoryUtil.memFree(lightData);
        }
        if (gridData != null) {
            MemoryUtil.memFree(gridData);
        }
        lightData = null;
        gridData = null;
        lightCount = 0;
    }

    private static void putTexel(ByteBuffer data, int floatIndex, float x, float y, float z, float w) {
        int offset = floatIndex * FLOAT_BYTES;
        data.putFloat(offset, x);
        data.putFloat(offset + FLOAT_BYTES, y);
        data.putFloat(offset + 2 * FLOAT_BYTES, z);
        data.putFloat(offset + 3 * FLOAT_BYTES, w);
    }
}
