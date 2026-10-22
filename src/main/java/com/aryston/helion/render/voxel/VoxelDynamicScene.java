package com.aryston.helion.render.voxel;

import java.util.List;

public record VoxelDynamicScene(List<VoxelDynamicLight> lights, List<VoxelOccluder> occluders) {
    public static final int MAX_LIGHTS = 16;
    public static final int MAX_OCCLUDERS = 8;
    public static final VoxelDynamicScene EMPTY = new VoxelDynamicScene(List.of(), List.of());

    public VoxelDynamicScene {
        lights = List.copyOf(lights.subList(0, Math.min(lights.size(), MAX_LIGHTS)));
        occluders = List.copyOf(occluders.subList(0, Math.min(occluders.size(), MAX_OCCLUDERS)));
    }
}
