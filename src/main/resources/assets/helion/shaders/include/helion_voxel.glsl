#ifndef HELION_VOXEL_GLSL
#define HELION_VOXEL_GLSL

const int HELION_VOXEL_SECTION_SIZE = 16;
const int HELION_VOXEL_SECTION_SHIFT = 4;
const int HELION_VOXEL_SECTION_MASK = 15;
const int HELION_VOXEL_AXIS_MASK = 7;
const int HELION_VOXEL_SECTIONS_PER_AXIS = 8;
const int HELION_VOXEL_SLICES_PER_ROW = 4;
const int HELION_VOXEL_BRICK_SIZE = 64;
const int HELION_VOXEL_BRICKS_PER_ROW = 32;
const int HELION_VOXEL_OCCUPANCY_MASK = 3;
const int HELION_VOXEL_EMISSION_SHIFT = 4;
const int HELION_VOXEL_EMPTY = 0;
const int HELION_VOXEL_OPAQUE = 1;
const int HELION_VOXEL_PARTIAL = 2;
const int HELION_VOXEL_TRANSLUCENT = 3;
const float HELION_VOXEL_BYTE_SCALE = 255.0;

bool helionVoxelInVolume(ivec3 block, ivec3 volumeMin, int volumeSize) {
    ivec3 offset = block - volumeMin;
    return all(greaterThanEqual(offset, ivec3(0))) && all(lessThan(offset, ivec3(volumeSize)));
}

ivec2 helionVoxelTexel(ivec3 block) {
    ivec3 slot = (block >> HELION_VOXEL_SECTION_SHIFT) & HELION_VOXEL_AXIS_MASK;
    ivec3 local = block & HELION_VOXEL_SECTION_MASK;
    int brick = slot.x + HELION_VOXEL_SECTIONS_PER_AXIS * (slot.y + HELION_VOXEL_SECTIONS_PER_AXIS * slot.z);
    ivec2 brickOrigin = ivec2(brick % HELION_VOXEL_BRICKS_PER_ROW, brick / HELION_VOXEL_BRICKS_PER_ROW) * HELION_VOXEL_BRICK_SIZE;
    ivec2 slice = ivec2(local.y % HELION_VOXEL_SLICES_PER_ROW, local.y / HELION_VOXEL_SLICES_PER_ROW) * HELION_VOXEL_SECTION_SIZE;
    return brickOrigin + slice + local.xz;
}

int helionVoxelFetch(sampler2D volume, ivec3 block) {
    return int(texelFetch(volume, helionVoxelTexel(block), 0).r * HELION_VOXEL_BYTE_SCALE + 0.5);
}

int helionVoxelOccupancy(int voxel) {
    return voxel & HELION_VOXEL_OCCUPANCY_MASK;
}

int helionVoxelEmission(int voxel) {
    return voxel >> HELION_VOXEL_EMISSION_SHIFT;
}

#endif
