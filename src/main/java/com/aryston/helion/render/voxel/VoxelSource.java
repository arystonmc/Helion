package com.aryston.helion.render.voxel;

import java.nio.ByteBuffer;
import java.util.function.LongConsumer;

public interface VoxelSource {
    int levelGeneration();

    void drainChangedSections(LongConsumer changed);

    void readSection(int sectionX, int sectionY, int sectionZ, ByteBuffer brick);
}
