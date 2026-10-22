#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_light_level.glsl>
#include <helion:helion_voxel.glsl>

layout(std140) uniform HelionVoxelLight {
    mat4 InverseViewProjection;
    vec4 CameraToVolume;
    ivec4 VolumeMin;
    ivec4 Trace;
};

uniform sampler2D DepthSampler;
uniform sampler2D GeometryNormalSampler;
uniform sampler2D VoxelSampler;
uniform sampler2D VoxelCoarseSampler;
uniform sampler2D VoxelReadySampler;
uniform sampler2D VoxelLightListSampler;
uniform sampler2D VoxelLightGridSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 lightColor;
layout(location = 1) out vec4 confidence;

const int HELION_MAX_TRACED_LIGHTS = 4;
const int HELION_LIGHTS_PER_CELL = 16;
const int HELION_LIGHT_CELL_SHIFT = 3;
const int HELION_LIGHT_CELLS_PER_AXIS = 16;
const int HELION_MAX_TRACE_STEPS = 48;
const int HELION_NO_LIGHT = -1;
const float HELION_GRID_SCALE = 65535.0;
const float HELION_EDGE_FADE_BLOCKS = 16.0;
const float HELION_SURFACE_OFFSET = 0.5;
const float HELION_SKY_DEPTH = 0.0;
const float HELION_MIN_DIRECTION = 1.0e-6;
const float HELION_UNLIT = 1.0e-5;
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

void main() {
    lightColor = vec4(0.0, 0.0, 0.0, 1.0);
    confidence = vec4(0.0);
    ivec2 fullSize = textureSize(DepthSampler, 0);
    ivec2 pixel = min(ivec2(texCoord * vec2(fullSize)), fullSize - 1);
    float depth = texelFetch(DepthSampler, pixel, 0).r;
    vec4 encodedNormal = texelFetch(GeometryNormalSampler, pixel, 0);
    if (depth <= HELION_SKY_DEPTH || encodedNormal.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        return;
    }
    vec3 normal = normalize(encodedNormal.rgb * 2.0 - 1.0);
    vec4 world = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, depth, 1.0);
    vec3 surface = world.xyz / world.w + CameraToVolume.xyz + normal * HELION_SURFACE_OFFSET;
    float edge = helionEdgeDistance(surface, CameraToVolume.w);
    ivec3 block = ivec3(floor(surface));
    if (edge <= 0.0 || !helionVoxelSlotReady(VoxelReadySampler, VolumeMin.xyz + block)) {
        return;
    }
    ivec3 cell = clamp(block >> HELION_LIGHT_CELL_SHIFT, ivec3(0), ivec3(HELION_LIGHT_CELLS_PER_AXIS - 1));
    int traced = min(Trace.x, HELION_MAX_TRACED_LIGHTS);
    float weights[HELION_LIGHTS_PER_CELL];
    vec3 positions[HELION_LIGHTS_PER_CELL];
    int count = 0;
    vec3 blended = vec3(0.0);
    float strongest = 0.0;
    for (int slot = 0; slot < HELION_LIGHTS_PER_CELL; slot++) {
        weights[slot] = 0.0;
        int light = helionGridLight(cell, slot);
        if (light == HELION_NO_LIGHT) {
            break;
        }
        count = slot + 1;
        vec4 source = texelFetch(VoxelLightListSampler, ivec2(light, 0), 0);
        positions[slot] = source.xyz;
        vec3 offset = abs(surface - source.xyz);
        float level = source.w - (offset.x + offset.y + offset.z);
        if (level <= 0.0) {
            continue;
        }
        vec3 term = texelFetch(VoxelLightListSampler, ivec2(light, 1), 0).rgb * helionLevelBrightness(level / HELION_LIGHT_LEVELS);
        weights[slot] = helionLuminance(term);
        blended += term;
        strongest = max(strongest, weights[slot]);
    }
    bool unknown = false;
    for (int attempt = 0; attempt < HELION_MAX_TRACED_LIGHTS; attempt++) {
        if (attempt >= traced) {
            break;
        }
        int pick = HELION_NO_LIGHT;
        for (int slot = 0; slot < HELION_LIGHTS_PER_CELL; slot++) {
            if (slot < count && weights[slot] > 0.0 && (pick == HELION_NO_LIGHT || weights[slot] > weights[pick])) {
                pick = slot;
            }
        }
        if (pick == HELION_NO_LIGHT) {
            break;
        }
        bool enclosed;
        vec3 exit = helionLightExit(positions[pick], surface, enclosed);
        bool visible = !enclosed && helionTraceVisibility(surface, exit, unknown) > 0.0;
        if (visible) {
            break;
        }
        weights[pick] = 0.0;
    }
    float visibleStrongest = 0.0;
    for (int slot = 0; slot < HELION_LIGHTS_PER_CELL; slot++) {
        visibleStrongest = max(visibleStrongest, slot < count ? weights[slot] : 0.0);
    }
    float blendedLuminance = helionLuminance(blended);
    vec3 unshadowed = blendedLuminance > HELION_UNLIT ? blended * (strongest / blendedLuminance) : vec3(0.0);
    float visibility = strongest > HELION_UNLIT ? visibleStrongest / strongest : 1.0;
    lightColor = vec4(unshadowed, visibility);
    confidence = vec4(unknown ? 0.0 : smoothstep(0.0, HELION_EDGE_FADE_BLOCKS, edge));
}
