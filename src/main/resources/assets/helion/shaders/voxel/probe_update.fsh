#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_light_level.glsl>
#include <helion:helion_voxel.glsl>
#include <helion:helion_voxel_probe.glsl>

layout(std140) uniform HelionVoxelLight {
    mat4 InverseViewProjection;
    vec4 CameraToVolume;
    ivec4 VolumeMin;
    ivec4 Trace;
};

layout(std140) uniform HelionVoxelProbe {
    mat4 RayRotation;
    ivec4 ProbeShift;
    vec4 ProbeSettings;
};

uniform sampler2D VoxelSampler;
uniform sampler2D VoxelCoarseSampler;
uniform sampler2D VoxelColorSampler;
uniform sampler2D VoxelReadySampler;
uniform sampler2D VoxelLightListSampler;
uniform sampler2D VoxelLightGridSampler;
uniform sampler2D PreviousProbeRedSampler;
uniform sampler2D PreviousProbeGreenSampler;
uniform sampler2D PreviousProbeBlueSampler;

#include <helion:helion_voxel_trace.glsl>

layout(location = 0) out vec4 probeRed;
layout(location = 1) out vec4 probeGreen;
layout(location = 2) out vec4 probeBlue;

const int HELION_MAX_PROBE_RAYS = 16;
const int HELION_MAX_PROBE_STEPS = 32;
const float HELION_GOLDEN_RATIO = 1.61803399;
const float HELION_TAU = 6.28318531;
const float HELION_SPHERE = 4.0 * 3.14159265;
const float HELION_HAS_HISTORY = 0.5;
const float HELION_HIT_OFFSET = 0.5;

struct HelionProbeHit {
    bool hit;
    ivec3 cell;
    ivec3 before;
};

vec3 helionFibonacciDirection(int index, int count) {
    float height = 1.0 - (2.0 * float(index) + 1.0) / float(count);
    float radius = sqrt(max(1.0 - height * height, 0.0));
    float angle = HELION_TAU * fract(float(index) / HELION_GOLDEN_RATIO);
    return vec3(cos(angle) * radius, height, sin(angle) * radius);
}

HelionProbeHit helionProbeRay(vec3 from, vec3 direction) {
    ivec3 cell = ivec3(floor(from));
    ivec3 stepping = ivec3(sign(direction));
    vec3 delta = 1.0 / max(abs(direction), vec3(HELION_MIN_DIRECTION));
    vec3 boundary = mix(from - vec3(cell), vec3(cell) + 1.0 - from, step(0.0, direction));
    vec3 next = boundary * delta;
    ivec3 before = cell;
    for (int index = 0; index < HELION_MAX_PROBE_STEPS; index++) {
        if (min(next.x, min(next.y, next.z)) > ProbeSettings.z) {
            break;
        }
        before = cell;
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
        if (!helionVoxelInVolume(VolumeMin.xyz + cell, VolumeMin.xyz, HELION_VOXEL_SECTION_SIZE * HELION_VOXEL_SECTIONS_PER_AXIS)
            || !helionVoxelSlotReady(VoxelReadySampler, VolumeMin.xyz + cell)) {
            break;
        }
        if (helionBlocksLight(cell)) {
            return HelionProbeHit(true, cell, before);
        }
    }
    return HelionProbeHit(false, cell, before);
}

vec3 helionHitLight(vec3 hitPoint, vec3 faceNormal) {
    ivec3 lightCell = helionLightCell(ivec3(floor(hitPoint)));
    vec3 total = vec3(0.0);
    float strongest = 0.0;
    vec3 strongestPosition = hitPoint;
    vec3 strongestTerm = vec3(0.0);
    for (int slot = 0; slot < HELION_LIGHTS_PER_CELL; slot++) {
        int light = helionGridLight(lightCell, slot);
        if (light == HELION_NO_LIGHT) {
            break;
        }
        vec4 source = texelFetch(VoxelLightListSampler, ivec2(light, 0), 0);
        vec3 offset = abs(hitPoint - source.xyz);
        float level = source.w - (offset.x + offset.y + offset.z);
        if (level <= 0.0 || dot(source.xyz - hitPoint, faceNormal) <= 0.0) {
            continue;
        }
        vec3 term = texelFetch(VoxelLightListSampler, ivec2(light, 1), 0).rgb * helionLevelBrightness(level / HELION_LIGHT_LEVELS);
        total += term;
        float weight = helionLuminance(term);
        if (weight > strongest) {
            strongest = weight;
            strongestPosition = source.xyz;
            strongestTerm = term;
        }
    }
    if (strongest <= 0.0) {
        return total;
    }
    bool enclosed;
    bool unknown = false;
    vec3 exit = helionLightExit(strongestPosition, hitPoint, enclosed);
    if (enclosed || helionTraceVisibility(hitPoint, exit, unknown) <= 0.0) {
        total -= strongestTerm;
    }
    return total;
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    ivec3 probe = helionProbeAt(texel);
    vec3 origin = helionProbePosition(probe);
    vec4 red = vec4(0.0);
    vec4 green = vec4(0.0);
    vec4 blue = vec4(0.0);
    int rays = min(int(ProbeSettings.y), HELION_MAX_PROBE_RAYS);
    bool insideWall = helionBlocksLight(ivec3(floor(origin)));
    for (int ray = 0; ray < HELION_MAX_PROBE_RAYS; ray++) {
        if (ray >= rays || insideWall) {
            break;
        }
        vec3 direction = normalize(mat3(RayRotation) * helionFibonacciDirection(ray, rays));
        HelionProbeHit hit = helionProbeRay(origin, direction);
        if (!hit.hit) {
            continue;
        }
        vec3 faceNormal = vec3(hit.before - hit.cell);
        vec3 hitPoint = vec3(hit.before) + HELION_HIT_OFFSET;
        vec3 albedo = texelFetch(VoxelColorSampler, helionVoxelTexel(VolumeMin.xyz + hit.cell), 0).rgb;
        vec3 radiance = albedo * helionHitLight(hitPoint, faceNormal) * ProbeSettings.w;
        vec4 basis = helionShBasis(direction);
        red += radiance.r * basis;
        green += radiance.g * basis;
        blue += radiance.b * basis;
    }
    float scale = rays > 0 ? HELION_SPHERE / float(rays) : 0.0;
    red *= scale;
    green *= scale;
    blue *= scale;
    ivec3 previous = probe + ProbeShift.xyz;
    if (ProbeSettings.x > 0.0 && float(ProbeShift.w) > HELION_HAS_HISTORY && helionProbeInGrid(previous)) {
        ivec2 history = helionProbeTexel(previous);
        red = mix(red, texelFetch(PreviousProbeRedSampler, history, 0), ProbeSettings.x);
        green = mix(green, texelFetch(PreviousProbeGreenSampler, history, 0), ProbeSettings.x);
        blue = mix(blue, texelFetch(PreviousProbeBlueSampler, history, 0), ProbeSettings.x);
    }
    probeRed = red;
    probeGreen = green;
    probeBlue = blue;
}
