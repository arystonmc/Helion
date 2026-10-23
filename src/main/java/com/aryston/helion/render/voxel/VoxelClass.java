package com.aryston.helion.render.voxel;

public final class VoxelClass {
    public static final byte EMPTY = 0;
    public static final byte OPAQUE = 1;
    public static final byte PARTIAL = 2;
    public static final byte TRANSLUCENT = 3;
    public static final int MAX_EMISSION = 15;
    public static final byte TINTS_LIGHT = 4;

    private static final int EMISSION_SHIFT = 4;

    private VoxelClass() {
    }

    public static byte encode(byte occupancy, int emission) {
        return (byte) (occupancy | Math.clamp(emission, 0, MAX_EMISSION) << EMISSION_SHIFT);
    }

    public static byte tinting(byte voxel) {
        return (byte) (voxel | TINTS_LIGHT);
    }

    public static boolean tints(byte voxel) {
        return (voxel & TINTS_LIGHT) != 0;
    }
}
