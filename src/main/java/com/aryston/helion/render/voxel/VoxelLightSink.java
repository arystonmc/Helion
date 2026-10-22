package com.aryston.helion.render.voxel;

public interface VoxelLightSink {
    void light(int x, int y, int z, int level, float red, float green, float blue);
}
