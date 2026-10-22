#ifndef HELION_VOXEL_TRACE_GLSL
#define HELION_VOXEL_TRACE_GLSL

const int HELION_LIGHTS_PER_CELL = 16;
const int HELION_LIGHT_CELL_SHIFT = 3;
const int HELION_LIGHT_CELLS_PER_AXIS = 16;
const int HELION_MAX_TRACE_STEPS = 48;
const int HELION_NO_LIGHT = -1;
const float HELION_GRID_SCALE = 65535.0;
const float HELION_SURFACE_OFFSET = 0.5;
const float HELION_SKY_DEPTH = 0.0;
const float HELION_MIN_DIRECTION = 1.0e-6;
const float HELION_EXIT_OFFSET = 0.55;
const int HELION_FACES = 6;
const ivec3 HELION_FACE_OFFSETS[HELION_FACES] = ivec3[](
    ivec3(1, 0, 0), ivec3(-1, 0, 0), ivec3(0, 1, 0), ivec3(0, -1, 0), ivec3(0, 0, 1), ivec3(0, 0, -1)
);

int helionGridLight(ivec3 cell, int slot) {
    int index = cell.x + HELION_LIGHT_CELLS_PER_AXIS * (cell.y + HELION_LIGHT_CELLS_PER_AXIS * cell.z);
    ivec2 texel = ivec2(index % HELION_LIGHT_CELLS_PER_AXIS * HELION_LIGHTS_PER_CELL + slot, index / HELION_LIGHT_CELLS_PER_AXIS);
    return int(texelFetch(VoxelLightGridSampler, texel, 0).r * HELION_GRID_SCALE + 0.5) - 1;
}

ivec3 helionLightCell(ivec3 block) {
    return clamp(block >> HELION_LIGHT_CELL_SHIFT, ivec3(0), ivec3(HELION_LIGHT_CELLS_PER_AXIS - 1));
}

bool helionBlocksLight(ivec3 relative) {
    ivec3 block = VolumeMin.xyz + relative;
    if (!helionVoxelCoarseBlocked(VoxelCoarseSampler, block)) {
        return false;
    }
    return helionVoxelOccupancy(helionVoxelFetch(VoxelSampler, block)) == HELION_VOXEL_OPAQUE;
}

float helionTraceVisibility(vec3 from, vec3 to, inout bool unknown) {
    vec3 direction = to - from;
    ivec3 cell = ivec3(floor(from));
    ivec3 target = ivec3(floor(to));
    ivec3 stepping = ivec3(sign(direction));
    vec3 delta = 1.0 / max(abs(direction), vec3(HELION_MIN_DIRECTION));
    vec3 boundary = mix(from - vec3(cell), vec3(cell) + 1.0 - from, step(0.0, direction));
    vec3 next = boundary * delta;
    for (int index = 0; index < HELION_MAX_TRACE_STEPS; index++) {
        if (min(next.x, min(next.y, next.z)) > 1.0) {
            return 1.0;
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
            return 1.0;
        }
        if (!helionVoxelSlotReady(VoxelReadySampler, VolumeMin.xyz + cell)) {
            unknown = true;
            return 1.0;
        }
        if (helionBlocksLight(cell)) {
            return 0.0;
        }
    }
    return 1.0;
}

vec3 helionLightExit(vec3 light, vec3 surface, out bool enclosed) {
    vec3 toSurface = surface - light;
    ivec3 lightBlock = ivec3(floor(light));
    float best = -2.0;
    vec3 exit = light;
    enclosed = true;
    for (int face = 0; face < HELION_FACES; face++) {
        ivec3 offset = HELION_FACE_OFFSETS[face];
        if (helionBlocksLight(lightBlock + offset)) {
            continue;
        }
        float facing = dot(vec3(offset), toSurface);
        if (facing > best) {
            best = facing;
            exit = light + vec3(offset) * HELION_EXIT_OFFSET;
            enclosed = false;
        }
    }
    return exit;
}

float helionEdgeDistance(vec3 position, float size) {
    vec3 inside = min(position, vec3(size) - position);
    return min(inside.x, min(inside.y, inside.z));
}

vec3 helionVoxelSurface(vec2 screen, float depth, vec3 normal) {
    vec4 world = InverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    return world.xyz / world.w + CameraToVolume.xyz + normal * HELION_SURFACE_OFFSET;
}

#endif
