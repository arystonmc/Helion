#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_light_level.glsl>
#include <helion:helion_voxel.glsl>
#include <helion:helion_hash.glsl>

const int HELION_VOXEL_DYNAMIC_LIGHTS = 16;
const int HELION_MAX_OCCLUDERS = 8;

layout(std140) uniform HelionVoxelLight {
    mat4 InverseViewProjection;
    vec4 CameraToVolume;
    ivec4 VolumeMin;
    ivec4 Trace;
    vec4 Flicker;
};

layout(std140) uniform HelionVoxelDynamic {
    ivec4 DynamicCounts;
    vec4 DynamicLights[HELION_VOXEL_DYNAMIC_LIGHTS * 2];
    vec4 Occluders[HELION_MAX_OCCLUDERS * 2];
};

uniform sampler2D DepthSampler;
uniform sampler2D GeometryNormalSampler;
uniform sampler2D VoxelSampler;
uniform sampler2D VoxelCoarseSampler;
uniform sampler2D VoxelColorSampler;
uniform sampler2D VoxelReadySampler;
uniform sampler2D VoxelLightListSampler;
uniform sampler2D VoxelLightGridSampler;

#include <helion:helion_voxel_trace.glsl>
#include <helion:helion_voxel_tint.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 lightColor;
layout(location = 1) out vec4 confidence;

const int HELION_EXTRA_TRACES = 1;
const int HELION_MAX_TRACED_LIGHTS = 8;
const int HELION_CANDIDATES = HELION_LIGHTS_PER_CELL + HELION_VOXEL_DYNAMIC_LIGHTS;
const float HELION_LIGHT_SPILL = 0.15;
const float HELION_TRACE_SHARE = 0.35;
const float HELION_EDGE_FADE_BLOCKS = 16.0;
const float HELION_UNLIT = 1.0e-5;
const float HELION_FLICKERING = 0.5;
const float HELION_FLICKER_STRENGTH = 0.07;
const float HELION_FLICKER_TAU = 6.28318531;
const vec3 HELION_FLICKER_RATES = vec3(5.3, 9.1, 15.7);
const vec3 HELION_FLICKER_WEIGHTS = vec3(0.5, 0.3, 0.2);
const vec3 HELION_FLICKER_PHASE_SCALE = vec3(1.0, 1.9, 3.1);
const uint HELION_FLICKER_DYNAMIC_SEED = 7919u;

float helionFlicker(float flickers, uint seed) {
    if (flickers < HELION_FLICKERING) {
        return 1.0;
    }
    float phase = float(helionPcg(seed)) / HELION_HASH_RANGE * HELION_FLICKER_TAU;
    vec3 waves = sin(Flicker.x * HELION_FLICKER_RATES + phase * HELION_FLICKER_PHASE_SCALE);
    return 1.0 + HELION_FLICKER_STRENGTH * dot(waves, HELION_FLICKER_WEIGHTS);
}

uint helionLightSeed(vec3 volumePosition) {
    ivec3 world = ivec3(floor(volumePosition)) + VolumeMin.xyz;
    return helionPcg(helionPcg(helionPcg(uint(world.x)) + uint(world.y)) + uint(world.z));
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

struct HelionLightSum {
    vec3 hue;
    float total;
    float peak;
};

void helionAddLight(inout HelionLightSum sum, vec3 term) {
    float luminance = helionLuminance(term);
    sum.hue += term;
    sum.total += luminance;
    sum.peak = max(sum.peak, luminance);
}

vec3 helionLightTotal(HelionLightSum sum) {
    float headroom = max(1.0 - sum.peak, 0.0);
    float luminance = sum.peak + headroom * (1.0 - exp(-(sum.total - sum.peak) * HELION_LIGHT_SPILL));
    return sum.total > HELION_UNLIT ? sum.hue * (luminance / sum.total) : vec3(0.0);
}

struct HelionCandidate {
    vec3 position;
    vec3 term;
};

HelionCandidate helionCandidate(ivec3 cell, vec3 surface, int candidate) {
    vec4 source;
    vec4 color;
    uint seed;
    if (candidate < HELION_LIGHTS_PER_CELL) {
        int light = helionGridLight(cell, candidate);
        source = texelFetch(VoxelLightListSampler, ivec2(light, 0), 0);
        color = texelFetch(VoxelLightListSampler, ivec2(light, 1), 0);
        seed = helionLightSeed(source.xyz);
    } else {
        int index = candidate - HELION_LIGHTS_PER_CELL;
        source = DynamicLights[index * 2];
        color = DynamicLights[index * 2 + 1];
        seed = helionPcg(uint(index) + HELION_FLICKER_DYNAMIC_SEED);
    }
    float level = source.w - distance(surface, source.xyz) * HELION_VOXEL_LIGHT_FALLOFF;
    vec3 term = level > 0.0 ? color.rgb * helionLevelBrightness(level / HELION_LIGHT_LEVELS) * helionFlicker(color.a, seed) : vec3(0.0);
    return HelionCandidate(source.xyz, term);
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
    vec3 surface = helionVoxelSurface(texCoord, depth, normal);
    float edge = helionEdgeDistance(surface, CameraToVolume.w);
    ivec3 block = ivec3(floor(surface));
    if (edge <= 0.0 || !helionVoxelSlotReady(VoxelReadySampler, VolumeMin.xyz + block)) {
        return;
    }
    ivec3 cell = helionLightCell(block);
    float weights[HELION_CANDIDATES];
    for (int candidate = 0; candidate < HELION_CANDIDATES; candidate++) {
        weights[candidate] = 0.0;
    }
    float strongest = 0.0;
    for (int slot = 0; slot < HELION_LIGHTS_PER_CELL; slot++) {
        if (helionGridLight(cell, slot) == HELION_NO_LIGHT) {
            break;
        }
        weights[slot] = helionLuminance(helionCandidate(cell, surface, slot).term);
        strongest = max(strongest, weights[slot]);
    }
    for (int index = 0; index < HELION_VOXEL_DYNAMIC_LIGHTS; index++) {
        if (index >= DynamicCounts.x) {
            break;
        }
        int candidate = HELION_LIGHTS_PER_CELL + index;
        weights[candidate] = helionLuminance(helionCandidate(cell, surface, candidate).term);
        strongest = max(strongest, weights[candidate]);
    }
    HelionLightSum unshadowed = HelionLightSum(vec3(0.0), 0.0, 0.0);
    HelionLightSum visible = HelionLightSum(vec3(0.0), 0.0, 0.0);
    bool unknown = false;
    float tracedWeight = 0.0;
    float visibleWeight = 0.0;
    int budget = min(Trace.x + HELION_EXTRA_TRACES, HELION_MAX_TRACED_LIGHTS);
    for (int attempt = 0; attempt < HELION_MAX_TRACED_LIGHTS; attempt++) {
        if (attempt >= budget) {
            break;
        }
        int pick = -1;
        for (int candidate = 0; candidate < HELION_CANDIDATES; candidate++) {
            if (weights[candidate] > 0.0 && (pick < 0 || weights[candidate] > weights[pick])) {
                pick = candidate;
            }
        }
        if (pick < 0 || (attempt > 0 && weights[pick] < strongest * HELION_TRACE_SHARE)) {
            break;
        }
        HelionCandidate light = helionCandidate(cell, surface, pick);
        vec3 target = light.position;
        bool enclosed = false;
        if (pick < HELION_LIGHTS_PER_CELL) {
            target = helionLightExit(light.position, surface, enclosed);
        }
        vec3 tint = enclosed ? vec3(0.0) : helionTraceTint(surface, target, unknown);
        if (helionOccluded(surface, target)) {
            tint = vec3(0.0);
        }
        helionAddLight(unshadowed, light.term);
        helionAddLight(visible, light.term * tint);
        tracedWeight += weights[pick];
        visibleWeight += helionLuminance(light.term * tint);
        weights[pick] = 0.0;
    }
    float share = tracedWeight > HELION_UNLIT ? visibleWeight / tracedWeight : 1.0;
    for (int candidate = 0; candidate < HELION_CANDIDATES; candidate++) {
        if (weights[candidate] > 0.0) {
            vec3 term = helionCandidate(cell, surface, candidate).term;
            helionAddLight(unshadowed, term);
            helionAddLight(visible, term * share);
        }
    }
    vec3 total = helionLightTotal(unshadowed);
    vec3 lit = helionLightTotal(visible);
    float totalLuminance = helionLuminance(total);
    float litLuminance = helionLuminance(lit);
    float visibility = totalLuminance > HELION_UNLIT ? litLuminance / totalLuminance : 1.0;
    vec3 litHue = litLuminance > HELION_UNLIT ? lit * (totalLuminance / litLuminance) : total;
    lightColor = vec4(litHue, visibility);
    confidence = vec4(unknown ? 0.0 : smoothstep(0.0, HELION_EDGE_FADE_BLOCKS, edge));
}
