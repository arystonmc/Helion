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

#endif
