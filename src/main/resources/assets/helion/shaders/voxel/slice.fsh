#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_voxel.glsl>

layout(std140) uniform HelionVoxelSlice {
    ivec4 CameraBlock;
    ivec4 VolumeMin;
    ivec4 OutputSize;
};

uniform sampler2D VoxelSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_SLICE_SCREEN_FRACTION = 0.9;
const float HELION_MAX_EMISSION = 15.0;
const vec3 HELION_SLICE_EMPTY = vec3(0.06);
const vec3 HELION_SLICE_OPAQUE = vec3(0.72);
const vec3 HELION_SLICE_PARTIAL = vec3(0.3, 0.7, 0.3);
const vec3 HELION_SLICE_TRANSLUCENT = vec3(0.25, 0.45, 0.95);
const vec3 HELION_SLICE_EMITTER = vec3(1.0, 0.75, 0.2);
const vec3 HELION_SLICE_CAMERA = vec3(1.0, 0.1, 0.1);
const vec3 HELION_SLICE_SECTION_EDGE = vec3(0.35, 0.0, 0.35);
const float HELION_SLICE_EDGE_MIX = 0.35;

vec3 helionSliceColor(int voxel) {
    int occupancy = helionVoxelOccupancy(voxel);
    vec3 color = HELION_SLICE_EMPTY;
    if (occupancy == HELION_VOXEL_OPAQUE) {
        color = HELION_SLICE_OPAQUE;
    } else if (occupancy == HELION_VOXEL_PARTIAL) {
        color = HELION_SLICE_PARTIAL;
    } else if (occupancy == HELION_VOXEL_TRANSLUCENT) {
        color = HELION_SLICE_TRANSLUCENT;
    }
    int emission = helionVoxelEmission(voxel);
    if (emission > 0) {
        color = mix(color, HELION_SLICE_EMITTER, float(emission) / HELION_MAX_EMISSION);
    }
    return color;
}

void main() {
    vec2 outputSize = vec2(OutputSize.xy);
    float side = min(outputSize.x, outputSize.y) * HELION_SLICE_SCREEN_FRACTION;
    vec2 squareMin = (outputSize - vec2(side)) * 0.5;
    vec2 mapPosition = (gl_FragCoord.xy - squareMin) / side;
    if (any(lessThan(mapPosition, vec2(0.0))) || any(greaterThanEqual(mapPosition, vec2(1.0)))) {
        discard;
    }
    int volumeSize = VolumeMin.w;
    ivec2 offset = ivec2(floor(mapPosition * float(volumeSize)));
    ivec3 block = ivec3(VolumeMin.x + offset.x, CameraBlock.y, VolumeMin.z + offset.y);
    if (!helionVoxelInVolume(block, VolumeMin.xyz, volumeSize)) {
        fragColor = vec4(HELION_SLICE_EMPTY, 1.0);
        return;
    }
    vec3 color = helionSliceColor(helionVoxelFetch(VoxelSampler, block));
    ivec2 local = block.xz & HELION_VOXEL_SECTION_MASK;
    if (any(equal(local, ivec2(0)))) {
        color = mix(color, HELION_SLICE_SECTION_EDGE, HELION_SLICE_EDGE_MIX);
    }
    if (block.xz == CameraBlock.xz) {
        color = HELION_SLICE_CAMERA;
    }
    fragColor = vec4(color, 1.0);
}
