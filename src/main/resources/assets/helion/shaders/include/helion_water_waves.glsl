#ifndef HELION_WATER_WAVES_GLSL
#define HELION_WATER_WAVES_GLSL

#include <helion:helion_hash.glsl>

const int HELION_SWELL_OCTAVES = 6;
const float HELION_SWELL_FREQUENCY[HELION_SWELL_OCTAVES] = float[](0.07, 0.13, 0.24, 0.45, 0.9, 1.9);
const float HELION_SWELL_HEIGHT[HELION_SWELL_OCTAVES] = float[](0.09, 0.07, 0.04, 0.018, 0.007, 0.0025);
const float HELION_SWELL_CRESTED[HELION_SWELL_OCTAVES] = float[](1.0, 1.0, 0.6, 0.0, 0.0, 0.0);
const vec2 HELION_SWELL_STRETCH[HELION_SWELL_OCTAVES] = vec2[](vec2(1.0, 0.45), vec2(1.0, 0.55), vec2(1.0, 0.7), vec2(1.0, 0.8), vec2(1.0, 0.9), vec2(1.0, 1.0));
const float HELION_SWELL_SHEAR[HELION_SWELL_OCTAVES] = float[](0.8, -0.6, 0.5, -0.4, 0.2, 0.0);
const float HELION_SWELL_TURN[HELION_SWELL_OCTAVES] = float[](0.35, 0.9, -0.5, 1.6, 2.4, -1.2);
const vec2 HELION_SWELL_DRIFT[HELION_SWELL_OCTAVES] = vec2[](vec2(0.05, 0.02), vec2(-0.04, 0.07), vec2(0.11, -0.06), vec2(-0.2, -0.13), vec2(0.38, 0.29), vec2(-0.7, 0.55));
const float HELION_SWELL_CREST_EDGE = 0.18;
const float HELION_SWELL_PROBE = 0.04;
const float HELION_SWELL_PROBE_SHARE = 0.6;
const float HELION_SWELL_TAU = 6.28318531;
const float HELION_SWELL_PERIOD = 2048.0;
const float HELION_SWELL_DETAIL_FROM = 0.15;
const float HELION_SWELL_DETAIL_TO = 0.45;

vec2 helionSwellGradient(vec2 cell) {
    float angle = helionHash(mod(cell, HELION_SWELL_PERIOD)) * HELION_SWELL_TAU;
    return vec2(cos(angle), sin(angle));
}

float helionSwellNoise(vec2 position) {
    vec2 cell = floor(position);
    vec2 inside = position - cell;
    vec2 blend = inside * inside * inside * (inside * (inside * 6.0 - 15.0) + 10.0);
    float corner00 = dot(helionSwellGradient(cell), inside);
    float corner10 = dot(helionSwellGradient(cell + vec2(1.0, 0.0)), inside - vec2(1.0, 0.0));
    float corner01 = dot(helionSwellGradient(cell + vec2(0.0, 1.0)), inside - vec2(0.0, 1.0));
    float corner11 = dot(helionSwellGradient(cell + vec2(1.0, 1.0)), inside - vec2(1.0, 1.0));
    return mix(mix(corner00, corner10, blend.x), mix(corner01, corner11, blend.x), blend.y);
}

float helionSwellHeight(vec2 position, float seconds, float footprint) {
    float height = 0.0;
    for (int octave = 0; octave < HELION_SWELL_OCTAVES; octave++) {
        float angle = HELION_SWELL_TURN[octave];
        vec2 turned = mat2(cos(angle), sin(angle), -sin(angle), cos(angle)) * position * HELION_SWELL_FREQUENCY[octave];
        turned.y += turned.x * HELION_SWELL_SHEAR[octave];
        float noise = helionSwellNoise(turned * HELION_SWELL_STRETCH[octave] + HELION_SWELL_DRIFT[octave] * seconds);
        float ridge = 1.0 - smoothstep(0.0, HELION_SWELL_CREST_EDGE, abs(noise));
        float shaped = mix(noise, ridge - 0.5, HELION_SWELL_CRESTED[octave]);
        float detail = 1.0 - smoothstep(HELION_SWELL_DETAIL_FROM, HELION_SWELL_DETAIL_TO, footprint * HELION_SWELL_FREQUENCY[octave]);
        height += shaped * HELION_SWELL_HEIGHT[octave] * detail;
    }
    return height;
}

vec2 helionSwellSlope(vec2 position, float seconds, float footprint) {
    float probe = max(HELION_SWELL_PROBE, footprint * HELION_SWELL_PROBE_SHARE);
    float alongX = helionSwellHeight(position + vec2(probe, 0.0), seconds, footprint) - helionSwellHeight(position - vec2(probe, 0.0), seconds, footprint);
    float alongZ = helionSwellHeight(position + vec2(0.0, probe), seconds, footprint) - helionSwellHeight(position - vec2(0.0, probe), seconds, footprint);
    return vec2(alongX, alongZ) / (2.0 * probe);
}

const int HELION_OCEAN_WAVES = 4;
const vec2 HELION_OCEAN_CYCLES[HELION_OCEAN_WAVES] = vec2[](vec2(55.0, 26.0), vec2(24.0, 90.0), vec2(131.0, -50.0), vec2(-118.0, 162.0));
const float HELION_OCEAN_PERIOD = 1024.0;
const float HELION_OCEAN_HEIGHT[HELION_OCEAN_WAVES] = float[](0.07, 0.045, 0.03, 0.015);
const float HELION_OCEAN_RISE = 0.16;
const float HELION_OCEAN_GRAVITY = 6.0;
const float HELION_OCEAN_WARP_CELLS = 32.0;
const float HELION_OCEAN_WARP = 3.0;
const float HELION_OCEAN_WARP_OFFSET = 7.0;
const float HELION_OCEAN_PROBE = 0.05;
const float HELION_OCEAN_SHORE_REACH = 4.0;
const float HELION_OCEAN_SHORE_CALM = 1.0;
const int HELION_OCEAN_SHORE_RADIUS = 4;
const int HELION_OCEAN_SHORE_WRAP = 127;
const float HELION_OCEAN_SHORE_WATER = 0.75;
const float HELION_OCEAN_BLOCK_CENTER = 0.5;

float helionOceanWarp(vec2 position) {
    vec2 cell = floor(position);
    vec2 inside = position - cell;
    vec2 blend = inside * inside * (3.0 - 2.0 * inside);
    float corner00 = dot(helionSwellGradient(mod(cell, HELION_OCEAN_WARP_CELLS)), inside);
    float corner10 = dot(helionSwellGradient(mod(cell + vec2(1.0, 0.0), HELION_OCEAN_WARP_CELLS)), inside - vec2(1.0, 0.0));
    float corner01 = dot(helionSwellGradient(mod(cell + vec2(0.0, 1.0), HELION_OCEAN_WARP_CELLS)), inside - vec2(0.0, 1.0));
    float corner11 = dot(helionSwellGradient(mod(cell + vec2(1.0, 1.0), HELION_OCEAN_WARP_CELLS)), inside - vec2(1.0, 1.0));
    return mix(mix(corner00, corner10, blend.x), mix(corner01, corner11, blend.x), blend.y);
}

float helionOceanHeight(vec2 position, float seconds) {
    float height = 0.0;
    vec2 warpPosition = position * HELION_OCEAN_WARP_CELLS / HELION_OCEAN_PERIOD;
    for (int wave = 0; wave < HELION_OCEAN_WAVES; wave++) {
        vec2 vector = HELION_OCEAN_CYCLES[wave] * HELION_SWELL_TAU / HELION_OCEAN_PERIOD;
        float warp = helionOceanWarp(warpPosition + float(wave) * HELION_OCEAN_WARP_OFFSET) * HELION_OCEAN_WARP;
        float phase = dot(vector, position) - sqrt(HELION_OCEAN_GRAVITY * length(vector)) * seconds + warp;
        height += sin(phase) * HELION_OCEAN_HEIGHT[wave];
    }
    return height;
}

vec2 helionOceanSlope(vec2 position, float seconds) {
    float center = helionOceanHeight(position, seconds);
    float alongX = helionOceanHeight(position + vec2(HELION_OCEAN_PROBE, 0.0), seconds);
    float alongZ = helionOceanHeight(position + vec2(0.0, HELION_OCEAN_PROBE), seconds);
    return vec2(alongX - center, alongZ - center) / HELION_OCEAN_PROBE;
}

float helionOceanOpenness(sampler2D shore, vec2 position) {
    ivec2 corner = ivec2(floor(position + HELION_OCEAN_BLOCK_CENTER));
    float nearest = HELION_OCEAN_SHORE_REACH;
    for (int x = -HELION_OCEAN_SHORE_RADIUS; x < HELION_OCEAN_SHORE_RADIUS; x++) {
        for (int z = -HELION_OCEAN_SHORE_RADIUS; z < HELION_OCEAN_SHORE_RADIUS; z++) {
            ivec2 column = corner + ivec2(x, z);
            if (texelFetch(shore, column & HELION_OCEAN_SHORE_WRAP, 0).r < HELION_OCEAN_SHORE_WATER) {
                nearest = min(nearest, length(vec2(column) + HELION_OCEAN_BLOCK_CENTER - position));
            }
        }
    }
    return smoothstep(HELION_OCEAN_SHORE_CALM, HELION_OCEAN_SHORE_REACH, nearest);
}

#endif
