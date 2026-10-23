#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_light_level.glsl>
#include <helion:helion_voxel.glsl>
#include <helion:helion_voxel_probe.glsl>

const int HELION_MAX_DYNAMIC_LIGHTS = 16;
const int HELION_MAX_OCCLUDERS = 8;

layout(std140) uniform HelionVoxelLight {
    mat4 InverseViewProjection;
    vec4 CameraToVolume;
    ivec4 VolumeMin;
    ivec4 Trace;
};

layout(std140) uniform HelionVoxelDynamic {
    ivec4 DynamicCounts;
    vec4 DynamicLights[HELION_MAX_DYNAMIC_LIGHTS * 2];
    vec4 Occluders[HELION_MAX_OCCLUDERS * 2];
};

uniform sampler2D DepthSampler;
uniform sampler2D GeometryNormalSampler;
uniform sampler2D VoxelSampler;
uniform sampler2D VoxelCoarseSampler;
uniform sampler2D VoxelReadySampler;
uniform sampler2D VoxelLightListSampler;
uniform sampler2D VoxelLightGridSampler;
uniform sampler2D ProbeRedSampler;
uniform sampler2D ProbeGreenSampler;
uniform sampler2D ProbeBlueSampler;
uniform sampler2D ProbePlacementSampler;

#include <helion:helion_voxel_trace.glsl>
#include <helion:helion_voxel_probe_gather.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 lightColor;
layout(location = 1) out vec4 confidence;
layout(location = 2) out vec4 bounce;

const int HELION_MAX_TRACED_LIGHTS = 4;
const float HELION_EDGE_FADE_BLOCKS = 16.0;
const float HELION_UNLIT = 1.0e-5;
const float HELION_BOUNCE_GAIN = 4.2;
const float HELION_VANILLA_FLOOR_WITH_BOUNCE = 0.0;
const float HELION_VANILLA_FLOOR_WITHOUT_BOUNCE = 1.0;

vec4 helionBounce(vec3 surface, vec3 normal) {
    if (Trace.z <= 0) {
        return vec4(0.0, 0.0, 0.0, HELION_VANILLA_FLOOR_WITHOUT_BOUNCE);
    }
    vec3 gathered = helionGatherProbes(surface, normal, ivec3(0), ProbeRedSampler, ProbeGreenSampler, ProbeBlueSampler, ProbePlacementSampler);
    return vec4(gathered * HELION_BOUNCE_GAIN, HELION_VANILLA_FLOOR_WITH_BOUNCE);
}

bool helionOccluded(vec3 from, vec3 to) {
    vec3 direction = to - from;
    vec3 safe = mix(direction, vec3(HELION_MIN_DIRECTION), lessThan(abs(direction), vec3(HELION_MIN_DIRECTION)));
    for (int index = 0; index < HELION_MAX_OCCLUDERS; index++) {
        if (index >= DynamicCounts.y) {
            break;
        }
        vec3 low = Occluders[index * 2].xyz;
        vec3 high = Occluders[index * 2 + 1].xyz;
        if (all(greaterThan(to, low)) && all(lessThan(to, high))) {
            continue;
        }
        vec3 first = (low - from) / safe;
        vec3 second = (high - from) / safe;
        vec3 near = min(first, second);
        vec3 far = max(first, second);
        float enter = max(near.x, max(near.y, near.z));
        float leave = min(far.x, min(far.y, far.z));
        if (enter <= leave && enter > 0.0 && enter < 1.0) {
            return true;
        }
    }
    return false;
}

void main() {
    lightColor = vec4(0.0, 0.0, 0.0, 1.0);
    confidence = vec4(0.0);
    bounce = vec4(0.0, 0.0, 0.0, HELION_VANILLA_FLOOR_WITHOUT_BOUNCE);
    ivec2 fullSize = textureSize(DepthSampler, 0);
    ivec2 pixel = min(ivec2(texCoord * vec2(fullSize)), fullSize - 1);
    float depth = texelFetch(DepthSampler, pixel, 0).r;
    vec4 encodedNormal = texelFetch(GeometryNormalSampler, pixel, 0);
    if (depth <= HELION_SKY_DEPTH || encodedNormal.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        return;
    }
    vec3 normal = normalize(encodedNormal.rgb * 2.0 - 1.0);
    vec3 surface = helionVoxelSurface(texCoord, depth, normal);
    float edge = helionEdgeDistance(surface, CameraToVolume.w);
    ivec3 block = ivec3(floor(surface));
    if (edge <= 0.0 || !helionVoxelSlotReady(VoxelReadySampler, VolumeMin.xyz + block)) {
        return;
    }
    ivec3 cell = helionLightCell(block);
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
    float movingWeight = 0.0;
    float otherMovingWeight = 0.0;
    vec3 movingPosition = vec3(0.0);
    for (int index = 0; index < HELION_MAX_DYNAMIC_LIGHTS; index++) {
        if (index >= DynamicCounts.x) {
            break;
        }
        vec4 source = DynamicLights[index * 2];
        float level = source.w - distance(surface, source.xyz);
        if (level <= 0.0) {
            continue;
        }
        vec3 term = DynamicLights[index * 2 + 1].rgb * helionLevelBrightness(level / HELION_LIGHT_LEVELS);
        float weight = helionLuminance(term);
        blended += term;
        strongest = max(strongest, weight);
        if (weight > movingWeight) {
            otherMovingWeight = movingWeight;
            movingWeight = weight;
            movingPosition = source.xyz;
        } else {
            otherMovingWeight = max(otherMovingWeight, weight);
        }
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
        float pickWeight = pick == HELION_NO_LIGHT ? 0.0 : weights[pick];
        if (movingWeight > pickWeight) {
            if (helionTraceVisibility(surface, movingPosition, unknown) > 0.0 && !helionOccluded(surface, movingPosition)) {
                break;
            }
            movingWeight = 0.0;
            continue;
        }
        if (pick == HELION_NO_LIGHT) {
            break;
        }
        bool enclosed;
        vec3 exit = helionLightExit(positions[pick], surface, enclosed);
        bool visible = !enclosed && helionTraceVisibility(surface, exit, unknown) > 0.0 && !helionOccluded(surface, exit);
        if (visible) {
            break;
        }
        weights[pick] = 0.0;
    }
    float visibleStrongest = max(movingWeight, otherMovingWeight);
    for (int slot = 0; slot < HELION_LIGHTS_PER_CELL; slot++) {
        visibleStrongest = max(visibleStrongest, slot < count ? weights[slot] : 0.0);
    }
    float blendedLuminance = helionLuminance(blended);
    vec3 unshadowed = blendedLuminance > HELION_UNLIT ? blended * (strongest / blendedLuminance) : vec3(0.0);
    float visibility = strongest > HELION_UNLIT ? visibleStrongest / strongest : 1.0;
    lightColor = vec4(unshadowed, visibility);
    bounce = helionBounce(surface, normal);
    confidence = vec4(unknown ? 0.0 : smoothstep(0.0, HELION_EDGE_FADE_BLOCKS, edge));
}
