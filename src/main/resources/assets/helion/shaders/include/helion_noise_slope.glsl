#ifndef HELION_NOISE_SLOPE_GLSL
#define HELION_NOISE_SLOPE_GLSL

#include <helion:helion_hash.glsl>

const float HELION_NOISE_SLOPE_TAU = 6.28318531;

vec2 helionNoiseSlopeGradient(vec2 cell) {
    float angle = helionHash(cell) * HELION_NOISE_SLOPE_TAU;
    return vec2(cos(angle), sin(angle));
}

vec2 helionNoiseSlope(vec2 position) {
    vec2 cell = floor(position);
    vec2 inside = position - cell;
    vec2 blend = inside * inside * inside * (inside * (inside * 6.0 - 15.0) + 10.0);
    vec2 blendSlope = 30.0 * inside * inside * (inside * (inside - 2.0) + 1.0);
    vec2 gradientA = helionNoiseSlopeGradient(cell);
    vec2 gradientB = helionNoiseSlopeGradient(cell + vec2(1.0, 0.0));
    vec2 gradientC = helionNoiseSlopeGradient(cell + vec2(0.0, 1.0));
    vec2 gradientD = helionNoiseSlopeGradient(cell + vec2(1.0, 1.0));
    float valueA = dot(gradientA, inside);
    float valueB = dot(gradientB, inside - vec2(1.0, 0.0));
    float valueC = dot(gradientC, inside - vec2(0.0, 1.0));
    float valueD = dot(gradientD, inside - vec2(1.0, 1.0));
    float corners = valueA - valueB - valueC + valueD;
    return gradientA + blend.x * (gradientB - gradientA) + blend.y * (gradientC - gradientA) + blend.x * blend.y * (gradientA - gradientB - gradientC + gradientD)
        + blendSlope * (blend.yx * corners + vec2(valueB, valueC) - valueA);
}

float helionNoiseValue(vec2 position) {
    vec2 cell = floor(position);
    vec2 inside = position - cell;
    vec2 blend = inside * inside * inside * (inside * (inside * 6.0 - 15.0) + 10.0);
    float valueA = dot(helionNoiseSlopeGradient(cell), inside);
    float valueB = dot(helionNoiseSlopeGradient(cell + vec2(1.0, 0.0)), inside - vec2(1.0, 0.0));
    float valueC = dot(helionNoiseSlopeGradient(cell + vec2(0.0, 1.0)), inside - vec2(0.0, 1.0));
    float valueD = dot(helionNoiseSlopeGradient(cell + vec2(1.0, 1.0)), inside - vec2(1.0, 1.0));
    return mix(mix(valueA, valueB, blend.x), mix(valueC, valueD, blend.x), blend.y);
}

#endif
