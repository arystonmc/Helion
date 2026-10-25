#ifndef HELION_WATER_WAVES_GLSL
#define HELION_WATER_WAVES_GLSL

#include <helion:helion_hash.glsl>
#include <helion:helion_noise_slope.glsl>

const int HELION_SWELL_OCTAVES = 6;
const float HELION_SWELL_FREQUENCY[HELION_SWELL_OCTAVES] = float[](0.07, 0.13, 0.24, 0.45, 0.9, 1.9);
const float HELION_SWELL_HEIGHT[HELION_SWELL_OCTAVES] = float[](0.05, 0.045, 0.035, 0.022, 0.011, 0.0045);
const vec2 HELION_SWELL_STRETCH[HELION_SWELL_OCTAVES] = vec2[](vec2(1.0, 0.45), vec2(1.0, 0.55), vec2(1.0, 0.7), vec2(1.0, 0.8), vec2(1.0, 0.9), vec2(1.0, 1.0));
const float HELION_SWELL_SHEAR[HELION_SWELL_OCTAVES] = float[](0.8, -0.6, 0.5, -0.4, 0.2, 0.0);
const float HELION_SWELL_TURN[HELION_SWELL_OCTAVES] = float[](0.35, 0.9, -0.5, 1.6, 2.4, -1.2);
const vec2 HELION_SWELL_DRIFT[HELION_SWELL_OCTAVES] = vec2[](vec2(0.05, 0.02), vec2(-0.04, 0.07), vec2(0.11, -0.06), vec2(-0.2, -0.13), vec2(0.38, 0.29), vec2(-0.7, 0.55));
const float HELION_SWELL_TAU = 6.28318531;
const float HELION_SWELL_PERIOD = 2048.0;
const float HELION_SWELL_DETAIL_FROM = 0.15;
const float HELION_SWELL_DETAIL_TO = 0.45;

vec2 helionSwellGradient(vec2 cell) {
    float angle = helionHash(mod(cell, HELION_SWELL_PERIOD)) * HELION_SWELL_TAU;
    return vec2(cos(angle), sin(angle));
}

vec2 helionSwellSlope(vec2 position, float seconds, float footprint) {
    vec2 slope = vec2(0.0);
    for (int octave = 0; octave < HELION_SWELL_OCTAVES; octave++) {
        float frequency = HELION_SWELL_FREQUENCY[octave];
        float detail = 1.0 - smoothstep(HELION_SWELL_DETAIL_FROM, HELION_SWELL_DETAIL_TO, footprint * frequency);
        if (detail <= 0.0) {
            continue;
        }
        float angle = HELION_SWELL_TURN[octave];
        mat2 turn = mat2(cos(angle), sin(angle), -sin(angle), cos(angle));
        mat2 shear = mat2(1.0, HELION_SWELL_SHEAR[octave], 0.0, 1.0);
        vec2 stretch = HELION_SWELL_STRETCH[octave];
        vec2 sheared = shear * (turn * position * frequency);
        vec2 gradient = helionNoiseSlope(sheared * stretch + HELION_SWELL_DRIFT[octave] * seconds) * stretch;
        slope += (gradient * shear * turn) * frequency * HELION_SWELL_HEIGHT[octave] * detail;
    }
    return slope;
}

const int HELION_CAPILLARY_OCTAVES = 4;
const float HELION_CAPILLARY_FREQUENCY[HELION_CAPILLARY_OCTAVES] = float[](1.15, 2.1, 3.7, 6.4);
const float HELION_CAPILLARY_SLOPE[HELION_CAPILLARY_OCTAVES] = float[](0.022, 0.018, 0.015, 0.012);
const float HELION_CAPILLARY_TURN[HELION_CAPILLARY_OCTAVES] = float[](0.2, 1.3, 2.5, 3.9);
const vec2 HELION_CAPILLARY_STRETCH[HELION_CAPILLARY_OCTAVES] = vec2[](vec2(1.0, 0.5), vec2(1.0, 0.6), vec2(1.0, 0.75), vec2(1.0, 0.9));
const vec2 HELION_CAPILLARY_DRIFT[HELION_CAPILLARY_OCTAVES] = vec2[](vec2(0.42, 0.15), vec2(-0.36, 0.52), vec2(0.66, -0.41), vec2(-0.78, -0.6));
const float HELION_CAPILLARY_VARIANCE = 0.8;
const float HELION_CAPILLARY_GUST_SCALE = 0.045;
const vec2 HELION_CAPILLARY_GUST_DRIFT = vec2(0.035, 0.02);
const float HELION_CAPILLARY_GUST_EDGE = 0.35;
const float HELION_CAPILLARY_CALM = 0.6;
const float HELION_CAPILLARY_GUSTY = 1.15;

float helionCapillaryGust(vec2 position, float seconds) {
    float gust = helionNoiseValue(position * HELION_CAPILLARY_GUST_SCALE + HELION_CAPILLARY_GUST_DRIFT * seconds);
    return mix(HELION_CAPILLARY_CALM, HELION_CAPILLARY_GUSTY, smoothstep(-HELION_CAPILLARY_GUST_EDGE, HELION_CAPILLARY_GUST_EDGE, gust));
}

float helionCapillaryDetail(float footprint, int octave) {
    return 1.0 - smoothstep(HELION_SWELL_DETAIL_FROM, HELION_SWELL_DETAIL_TO, footprint * HELION_CAPILLARY_FREQUENCY[octave]);
}

vec2 helionCapillarySlope(vec2 position, float seconds, float footprint) {
    vec2 slope = vec2(0.0);
    for (int octave = 0; octave < HELION_CAPILLARY_OCTAVES; octave++) {
        float detail = helionCapillaryDetail(footprint, octave);
        if (detail <= 0.0) {
            continue;
        }
        float angle = HELION_CAPILLARY_TURN[octave];
        mat2 turn = mat2(cos(angle), sin(angle), -sin(angle), cos(angle));
        vec2 stretch = HELION_CAPILLARY_STRETCH[octave];
        vec2 cell = (turn * position) * HELION_CAPILLARY_FREQUENCY[octave] * stretch + HELION_CAPILLARY_DRIFT[octave] * seconds;
        slope += ((helionNoiseSlope(cell) * stretch) * turn) * HELION_CAPILLARY_SLOPE[octave] * detail;
    }
    return slope;
}

float helionCapillaryLostVariance(float footprint) {
    float lost = 0.0;
    for (int octave = 0; octave < HELION_CAPILLARY_OCTAVES; octave++) {
        lost += (1.0 - helionCapillaryDetail(footprint, octave)) * HELION_CAPILLARY_SLOPE[octave] * HELION_CAPILLARY_SLOPE[octave];
    }
    return lost * HELION_CAPILLARY_VARIANCE;
}

const float HELION_SHORE_LIMIT = 16.0;
const float HELION_SHORE_WINDOW = 128.0;
const float HELION_SHORE_REACH = 15.0;
const float HELION_SHORE_RISE_FROM = 0.2;
const float HELION_SHORE_EDGE = 0.8;
const float HELION_SHORE_NUMBER = 0.85;
const float HELION_SHORE_SHOALING = 1.4;
const float HELION_SHORE_SPEED = 1.7;
const float HELION_SHORE_HEIGHT = 0.14;
const float HELION_SHORE_SHARPNESS = 2.5;
const float HELION_SHORE_FETCH_PROBE = 6.0;
const float HELION_SHORE_FETCH_FROM = 3.0;
const float HELION_SHORE_FETCH_TO = 9.0;
const float HELION_SHORE_WARP_SCALE = 0.06;
const float HELION_SHORE_WARP_DRIFT = 0.02;
const float HELION_SHORE_WARP = 2.5;
const float HELION_SHORE_MIN_GRADIENT = 1.0e-4;
const float HELION_SHORE_BREAK_CREST = 0.6;
const float HELION_SHORE_BREAK_NEAR = 0.4;
const float HELION_SHORE_BREAK_FAR = 5.0;
const float HELION_SHORE_FOAM_SCALE = 1.8;
const vec2 HELION_SHORE_FOAM_DRIFT = vec2(0.13, -0.09);
const float HELION_SHORE_FOAM_FROM = -0.15;
const float HELION_SHORE_FOAM_TO = 0.25;

struct HelionShoreWave {
    vec2 slope;
    float foam;
};

float helionShoreDistance(sampler2D distances, vec2 position) {
    return textureLod(distances, position / HELION_SHORE_WINDOW, 0.0).r * HELION_SHORE_LIMIT;
}

HelionShoreWave helionShoreWave(sampler2D distances, vec2 position, float seconds) {
    HelionShoreWave wave = HelionShoreWave(vec2(0.0), 0.0);
    float shoreDistance = helionShoreDistance(distances, position);
    if (shoreDistance >= HELION_SHORE_REACH) {
        return wave;
    }
    vec2 away = vec2(
        helionShoreDistance(distances, position + vec2(1.0, 0.0)) - helionShoreDistance(distances, position - vec2(1.0, 0.0)),
        helionShoreDistance(distances, position + vec2(0.0, 1.0)) - helionShoreDistance(distances, position - vec2(0.0, 1.0))
    ) * 0.5;
    float gradient = dot(away, away);
    if (gradient < HELION_SHORE_MIN_GRADIENT) {
        return wave;
    }
    vec2 seaward = away * inversesqrt(gradient);
    float fetch = smoothstep(HELION_SHORE_FETCH_FROM, HELION_SHORE_FETCH_TO, helionShoreDistance(distances, position + seaward * HELION_SHORE_FETCH_PROBE));
    float rise = (1.0 - smoothstep(HELION_SHORE_REACH * HELION_SHORE_RISE_FROM, HELION_SHORE_REACH, shoreDistance)) * smoothstep(0.0, HELION_SHORE_EDGE, shoreDistance);
    float amplitude = HELION_SHORE_HEIGHT * fetch * rise;
    if (amplitude <= 0.0) {
        return wave;
    }
    float number = HELION_SHORE_NUMBER * (1.0 + HELION_SHORE_SHOALING * (1.0 - shoreDistance / HELION_SHORE_REACH));
    float travelled = shoreDistance + HELION_SHORE_SHOALING * (shoreDistance - shoreDistance * shoreDistance / (2.0 * HELION_SHORE_REACH));
    float warp = helionNoiseValue(position * HELION_SHORE_WARP_SCALE + HELION_SHORE_WARP_DRIFT * seconds) * HELION_SHORE_WARP;
    float phase = HELION_SHORE_NUMBER * travelled + HELION_SHORE_SPEED * seconds + warp;
    float crest = 0.5 + 0.5 * sin(phase);
    float steepness = amplitude * HELION_SHORE_SHARPNESS * pow(crest, HELION_SHORE_SHARPNESS - 1.0) * 0.5 * cos(phase) * number;
    wave.slope = seaward * steepness;
    float breaking = smoothstep(HELION_SHORE_BREAK_CREST, 1.0, crest) * (1.0 - smoothstep(HELION_SHORE_BREAK_NEAR, HELION_SHORE_BREAK_FAR, shoreDistance));
    float breakup = smoothstep(HELION_SHORE_FOAM_FROM, HELION_SHORE_FOAM_TO, helionNoiseValue(position * HELION_SHORE_FOAM_SCALE + HELION_SHORE_FOAM_DRIFT * seconds));
    wave.foam = fetch * breaking * breakup;
    return wave;
}

const int HELION_OCEAN_WAVES = 4;
const vec2 HELION_OCEAN_CYCLES[HELION_OCEAN_WAVES] = vec2[](vec2(55.0, 26.0), vec2(24.0, 90.0), vec2(131.0, -50.0), vec2(-118.0, 162.0));
const float HELION_OCEAN_PERIOD = 1024.0;
const float HELION_OCEAN_HEIGHT[HELION_OCEAN_WAVES] = float[](0.04, 0.025, 0.015, 0.008);
const float HELION_OCEAN_RISE = 0.088;
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
