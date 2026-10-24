#ifndef HELION_CAUSTICS_GLSL
#define HELION_CAUSTICS_GLSL

#include <helion:helion_hash.glsl>

const int HELION_CAUSTIC_OCTAVES = 3;
const float HELION_CAUSTIC_FREQUENCY[HELION_CAUSTIC_OCTAVES] = float[](1.0, 2.1, 4.3);
const float HELION_CAUSTIC_HEIGHT[HELION_CAUSTIC_OCTAVES] = float[](0.05, 0.012, 0.002);
const float HELION_CAUSTIC_TURN[HELION_CAUSTIC_OCTAVES] = float[](0.6, 1.9, 2.8);
const vec2 HELION_CAUSTIC_DRIFT[HELION_CAUSTIC_OCTAVES] = vec2[](vec2(0.23, 0.17), vec2(-0.31, 0.26), vec2(0.42, -0.37));
const float HELION_CAUSTIC_TAU = 6.28318531;
const float HELION_CAUSTIC_PROBE = 0.05;
const float HELION_CAUSTIC_FOCUS = 0.5;
const float HELION_CAUSTIC_MIN_DEPTH = 1.5;
const float HELION_CAUSTIC_MAX_DEPTH = 4.0;
const float HELION_CAUSTIC_MIN_AREA = 0.08;
const float HELION_CAUSTIC_MAX = 3.0;

vec2 helionCausticGradientAt(vec2 cell) {
    float angle = helionHash(cell) * HELION_CAUSTIC_TAU;
    return vec2(cos(angle), sin(angle));
}

vec2 helionCausticNoiseSlope(vec2 position) {
    vec2 cell = floor(position);
    vec2 inside = position - cell;
    vec2 blend = inside * inside * inside * (inside * (inside * 6.0 - 15.0) + 10.0);
    vec2 blendSlope = 30.0 * inside * inside * (inside * (inside - 2.0) + 1.0);
    vec2 gradientA = helionCausticGradientAt(cell);
    vec2 gradientB = helionCausticGradientAt(cell + vec2(1.0, 0.0));
    vec2 gradientC = helionCausticGradientAt(cell + vec2(0.0, 1.0));
    vec2 gradientD = helionCausticGradientAt(cell + vec2(1.0, 1.0));
    float valueA = dot(gradientA, inside);
    float valueB = dot(gradientB, inside - vec2(1.0, 0.0));
    float valueC = dot(gradientC, inside - vec2(0.0, 1.0));
    float valueD = dot(gradientD, inside - vec2(1.0, 1.0));
    float corners = valueA - valueB - valueC + valueD;
    return gradientA + blend.x * (gradientB - gradientA) + blend.y * (gradientC - gradientA) + blend.x * blend.y * (gradientA - gradientB - gradientC + gradientD)
        + blendSlope * (blend.yx * corners + vec2(valueB, valueC) - valueA);
}

vec2 helionCausticSlope(vec2 position, float seconds) {
    vec2 slope = vec2(0.0);
    for (int octave = 0; octave < HELION_CAUSTIC_OCTAVES; octave++) {
        float frequency = HELION_CAUSTIC_FREQUENCY[octave];
        mat2 turn = mat2(cos(HELION_CAUSTIC_TURN[octave]), sin(HELION_CAUSTIC_TURN[octave]), -sin(HELION_CAUSTIC_TURN[octave]), cos(HELION_CAUSTIC_TURN[octave]));
        vec2 local = helionCausticNoiseSlope(turn * position * frequency + HELION_CAUSTIC_DRIFT[octave] * seconds) * frequency * HELION_CAUSTIC_HEIGHT[octave];
        slope += local * turn;
    }
    return slope;
}

float helionCausticLight(vec2 position, float depth, float seconds) {
    float focus = clamp(depth, HELION_CAUSTIC_MIN_DEPTH, HELION_CAUSTIC_MAX_DEPTH) * HELION_CAUSTIC_FOCUS;
    vec2 center = helionCausticSlope(position, seconds);
    vec2 alongX = (helionCausticSlope(position + vec2(HELION_CAUSTIC_PROBE, 0.0), seconds) - center) / HELION_CAUSTIC_PROBE;
    vec2 alongZ = (helionCausticSlope(position + vec2(0.0, HELION_CAUSTIC_PROBE), seconds) - center) / HELION_CAUSTIC_PROBE;
    float area = (1.0 + focus * alongX.x) * (1.0 + focus * alongZ.y) - focus * focus * alongX.y * alongZ.x;
    return min(1.0 / max(abs(area), HELION_CAUSTIC_MIN_AREA), HELION_CAUSTIC_MAX);
}

#endif
