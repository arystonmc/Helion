#ifndef HELION_WATER_WAVES_GLSL
#define HELION_WATER_WAVES_GLSL

const int HELION_SWELL_OCTAVES = 5;
const float HELION_SWELL_FREQUENCY[HELION_SWELL_OCTAVES] = float[](0.31, 0.67, 1.43, 2.97, 6.1);
const float HELION_SWELL_HEIGHT[HELION_SWELL_OCTAVES] = float[](0.07, 0.035, 0.016, 0.0075, 0.0035);
const vec2 HELION_SWELL_DRIFT[HELION_SWELL_OCTAVES] = vec2[](vec2(0.31, 0.12), vec2(-0.22, 0.37), vec2(0.45, -0.28), vec2(-0.61, -0.43), vec2(0.83, 0.71));
const float HELION_SWELL_ROTATION = 1.07;
const float HELION_SWELL_PROBE = 0.04;
const float HELION_SWELL_TAU = 6.28318531;
const vec2 HELION_SWELL_HASH_STEP = vec2(269.5, 183.3);
const float HELION_SWELL_HASH_SCALE = 43758.5453;
const float HELION_SWELL_PERIOD = 2048.0;

vec2 helionSwellGradient(vec2 cell) {
    float angle = fract(sin(dot(mod(cell, HELION_SWELL_PERIOD), HELION_SWELL_HASH_STEP)) * HELION_SWELL_HASH_SCALE) * HELION_SWELL_TAU;
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

float helionSwellHeight(vec2 position, float seconds) {
    float height = 0.0;
    mat2 turn = mat2(cos(HELION_SWELL_ROTATION), sin(HELION_SWELL_ROTATION), -sin(HELION_SWELL_ROTATION), cos(HELION_SWELL_ROTATION));
    vec2 warped = position;
    for (int octave = 0; octave < HELION_SWELL_OCTAVES; octave++) {
        warped = turn * warped;
        height += helionSwellNoise(warped * HELION_SWELL_FREQUENCY[octave] + HELION_SWELL_DRIFT[octave] * seconds) * HELION_SWELL_HEIGHT[octave];
    }
    return height;
}

vec2 helionSwellSlope(vec2 position, float seconds) {
    float center = helionSwellHeight(position, seconds);
    float alongX = helionSwellHeight(position + vec2(HELION_SWELL_PROBE, 0.0), seconds);
    float alongZ = helionSwellHeight(position + vec2(0.0, HELION_SWELL_PROBE), seconds);
    return vec2(alongX - center, alongZ - center) / HELION_SWELL_PROBE;
}

const int HELION_OCEAN_WAVES = 4;
const vec2 HELION_OCEAN_CYCLES[HELION_OCEAN_WAVES] = vec2[](vec2(55.0, 26.0), vec2(24.0, 90.0), vec2(131.0, -50.0), vec2(-118.0, 162.0));
const float HELION_OCEAN_PERIOD = 1024.0;
const float HELION_OCEAN_HEIGHT[HELION_OCEAN_WAVES] = float[](0.11, 0.07, 0.045, 0.028);
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
