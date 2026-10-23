#ifndef HELION_VOXEL_TINT_GLSL
#define HELION_VOXEL_TINT_GLSL

const int HELION_VOXEL_TINTS_LIGHT = 4;

vec3 helionTraceTint(vec3 from, vec3 to, inout bool unknown) {
    vec3 direction = to - from;
    ivec3 cell = ivec3(floor(from));
    ivec3 target = ivec3(floor(to));
    ivec3 stepping = ivec3(sign(direction));
    vec3 delta = 1.0 / max(abs(direction), vec3(HELION_MIN_DIRECTION));
    vec3 boundary = mix(from - vec3(cell), vec3(cell) + 1.0 - from, step(0.0, direction));
    vec3 next = boundary * delta;
    vec3 tint = vec3(1.0);
    for (int index = 0; index < HELION_MAX_TRACE_STEPS; index++) {
        if (min(next.x, min(next.y, next.z)) > 1.0) {
            return tint;
        }
        if (next.x <= next.y && next.x <= next.z) {
            cell.x += stepping.x;
            next.x += delta.x;
        } else if (next.y <= next.z) {
            cell.y += stepping.y;
            next.y += delta.y;
        } else {
            cell.z += stepping.z;
            next.z += delta.z;
        }
        if (cell == target) {
            return tint;
        }
        ivec3 block = VolumeMin.xyz + cell;
        if (!helionVoxelSlotReady(VoxelReadySampler, block)) {
            unknown = true;
            return tint;
        }
        if (!helionVoxelCoarseBlocked(VoxelCoarseSampler, block)) {
            continue;
        }
        int voxel = helionVoxelFetch(VoxelSampler, block);
        if (helionVoxelOccupancy(voxel) == HELION_VOXEL_OPAQUE) {
            return vec3(0.0);
        }
        if ((voxel & HELION_VOXEL_TINTS_LIGHT) != 0) {
            tint *= texelFetch(VoxelColorSampler, helionVoxelTexel(block), 0).rgb;
        }
    }
    return tint;
}

#endif