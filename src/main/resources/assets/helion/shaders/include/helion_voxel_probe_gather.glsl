#ifndef HELION_VOXEL_PROBE_GATHER_GLSL
#define HELION_VOXEL_PROBE_GATHER_GLSL

const int HELION_PROBE_CORNERS = 8;
const float HELION_MIN_PROBE_WEIGHT = 1.0e-3;
const float HELION_MIN_FACING = 0.05;
const float HELION_MIN_PROBE_DISTANCE = 1.0e-3;

vec3 helionGatherProbes(vec3 surface, vec3 normal, ivec3 shift, sampler2D red, sampler2D green, sampler2D blue, sampler2D placement) {
    vec3 grid = surface / HELION_PROBE_SPACING - 0.5;
    ivec3 base = ivec3(floor(grid));
    vec3 fraction = grid - vec3(base);
    vec3 total = vec3(0.0);
    float weights = 0.0;
    bool unknown = false;
    for (int corner = 0; corner < HELION_PROBE_CORNERS; corner++) {
        ivec3 offset = ivec3(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1);
        ivec3 probe = base + offset;
        ivec3 stored = probe + shift;
        vec3 trilinear = mix(1.0 - fraction, fraction, vec3(offset));
        float weight = trilinear.x * trilinear.y * trilinear.z;
        if (!helionProbeInGrid(probe) || !helionProbeInGrid(stored) || weight <= HELION_MIN_PROBE_WEIGHT) {
            continue;
        }
        ivec2 texel = helionProbeTexel(stored);
        vec4 place = texelFetch(placement, texel, 0);
        if (place.a < HELION_PROBE_PLACED) {
            continue;
        }
        vec3 position = helionProbePlaced(probe, place.xyz);
        vec3 toProbe = position - surface;
        float facing = length(toProbe) > HELION_MIN_PROBE_DISTANCE ? dot(normalize(toProbe), normal) * 0.5 + 0.5 : 1.0;
        weight *= facing * facing + HELION_MIN_FACING;
        if (helionTraceVisibility(surface, position, unknown) <= 0.0) {
            continue;
        }
        vec3 irradiance = vec3(
            helionShIrradiance(texelFetch(red, texel, 0), normal),
            helionShIrradiance(texelFetch(green, texel, 0), normal),
            helionShIrradiance(texelFetch(blue, texel, 0), normal)
        );
        total += irradiance * weight;
        weights += weight;
    }
    return weights > 0.0 ? total / weights : vec3(0.0);
}

vec3 helionNearestProbe(vec3 surface, vec3 normal, ivec3 shift, sampler2D red, sampler2D green, sampler2D blue, sampler2D placement) {
    ivec3 nearest = ivec3(floor(surface / HELION_PROBE_SPACING));
    ivec3 stored = nearest + shift;
    if (!helionProbeInGrid(nearest) || !helionProbeInGrid(stored)) {
        return vec3(0.0);
    }
    ivec2 texel = helionProbeTexel(stored);
    vec4 place = texelFetch(placement, texel, 0);
    bool unknown = false;
    if (place.a < HELION_PROBE_PLACED || helionTraceVisibility(surface, helionProbePlaced(nearest, place.xyz), unknown) <= 0.0) {
        return vec3(0.0);
    }
    return vec3(
        helionShIrradiance(texelFetch(red, texel, 0), normal),
        helionShIrradiance(texelFetch(green, texel, 0), normal),
        helionShIrradiance(texelFetch(blue, texel, 0), normal)
    );
}

#endif
