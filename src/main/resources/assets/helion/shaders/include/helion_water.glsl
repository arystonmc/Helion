#ifndef HELION_WATER_GLSL
#define HELION_WATER_GLSL

const float HELION_WATER_MIN_THICKNESS = 1.0e-3;
const float HELION_WATER_MAX_THICKNESS = 64.0;
const float HELION_GLOSSY_MARKER = -1.0;
const float HELION_GLOSSY_THRESHOLD = -0.5;
const float HELION_WATER_TINT_FALLING = 0.25;
const float HELION_WATER_TINT_STILL = 0.5;
const float HELION_WATER_TINT_FALLING_BELOW = 0.375;
const float HELION_WATER_ROUGHNESS_RANGE = 0.5;

float helionEncodeWaterRoughness(float roughness) {
    return HELION_WATER_TINT_STILL + (1.0 - HELION_WATER_TINT_STILL) * clamp(roughness / HELION_WATER_ROUGHNESS_RANGE, 0.0, 1.0);
}

float helionDecodeWaterRoughness(float encoded) {
    return max(encoded - HELION_WATER_TINT_STILL, 0.0) / (1.0 - HELION_WATER_TINT_STILL) * HELION_WATER_ROUGHNESS_RANGE;
}

bool helionIsFallingWater(float encoded) {
    return encoded < HELION_WATER_TINT_FALLING_BELOW;
}

vec2 helionSignNotZero(vec2 value) {
    return vec2(value.x >= 0.0 ? 1.0 : -1.0, value.y >= 0.0 ? 1.0 : -1.0);
}

vec2 helionEncodeOctahedral(vec3 normal) {
    vec3 folded = normal / (abs(normal.x) + abs(normal.y) + abs(normal.z));
    return folded.y >= 0.0 ? folded.xz : (1.0 - abs(folded.zx)) * helionSignNotZero(folded.xz);
}

vec3 helionDecodeOctahedral(vec2 encoded) {
    vec3 normal = vec3(encoded.x, 1.0 - abs(encoded.x) - abs(encoded.y), encoded.y);
    if (normal.y < 0.0) {
        normal.xz = (1.0 - abs(normal.zx)) * helionSignNotZero(normal.xz);
    }
    return normalize(normal);
}

bool helionIsWater(vec4 surface) {
    return surface.z >= HELION_WATER_MIN_THICKNESS;
}

bool helionIsGlossy(vec4 surface) {
    return surface.z < HELION_GLOSSY_THRESHOLD;
}

#endif
