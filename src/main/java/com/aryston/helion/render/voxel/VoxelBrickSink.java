package com.aryston.helion.render.voxel;

import java.nio.ByteBuffer;

public interface VoxelBrickSink {
    void brickBuilt(int sectionX, int sectionY, int sectionZ, ByteBuffer brick);
}
