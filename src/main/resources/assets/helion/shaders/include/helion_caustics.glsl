#ifndef HELION_CAUSTICS_GLSL
#define HELION_CAUSTICS_GLSL

#include <helion:helion_noise_slope.glsl>

const int HELION_CAUSTIC_OCTAVES = 3;
const float HELION_CAUSTIC_FREQUENCY[HELION_CAUSTIC_OCTAVES] = float[](0.8, 1.7, 3.6);
const float HELION_CAUSTIC_HEIGHT[HELION_CAUSTIC_OCTAVES] = float[](0.08, 0.02, 0.004);
const float HELION_CAUSTIC_TURN[HELION_CAUSTIC_OCTAVES] = float[](0.6, 1.9, 2.8);
const vec2 HELION_CAUSTIC_DRIFT[HELION_CAUSTIC_OCTAVES] = vec2[](vec2(0.23, 0.17), vec2(-0.31, 0.26), vec2(0.42, -0.37));
const float HELION_CAUSTIC_PROBE = 0.04;
const float HELION_CAUSTIC_FOCUS = 0.55;
const float HELION_CAUSTIC_MIN_DEPTH = 1.5;
const float HELION_CAUSTIC_MAX_DEPTH = 5.0;
const float HELION_CAUSTIC_MIN_AREA = 0.03;
const float HELION_CAUSTIC_MAX = 6.0;
const float HELION_CAUSTIC_SHADE = 0.25;

vec2 helionCausticSlope(vec2 position, float seconds) {
    vec2 slope = vec2(0.0);
    for (int octave = 0; octave < HELION_CAUSTIC_OCTAVES; octave++) {
        float frequency = HELION_CAUSTIC_FREQUENCY[octave];
        mat2 turn = mat2(cos(HELION_CAUSTIC_TURN[octave]), sin(HELION_CAUSTIC_TURN[octave]), -sin(HELION_CAUSTIC_TURN[octave]), cos(HELION_CAUSTIC_TURN[octave]));
        vec2 local = helionNoiseSlope(turn * position * frequency + HELION_CAUSTIC_DRIFT[octave] * seconds) * frequency * HELION_CAUSTIC_HEIGHT[octave];
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

float helionCausticGain(float caustic, float share) {
    float lift = caustic - 1.0;
    return 1.0 + share * (lift > 0.0 ? lift : lift * HELION_CAUSTIC_SHADE);
}

#endif
