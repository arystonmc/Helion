#ifndef HELION_LIGHT_LEVEL_GLSL
#define HELION_LIGHT_LEVEL_GLSL

const float HELION_LIGHT_LEVELS = 15.0;
const float HELION_BRIGHTNESS_CURVE = 3.0;
const float HELION_VOXEL_LIGHT_FALLOFF = 1.1;
const float HELION_LIGHT_SATURATION = 0.75;
const vec3 HELION_LIGHT_LUMA = vec3(0.2126, 0.7152, 0.0722);
const float HELION_LIGHT_MIN_LUMA = 1.0e-4;
const float HELION_LIGHT_MAX_CHANNEL = 1.25;

float helionLevelBrightness(float level) {
    return level / (HELION_BRIGHTNESS_CURVE + 1.0 - HELION_BRIGHTNESS_CURVE * level);
}

vec3 helionBalancedLightColor(vec3 color) {
    vec3 balanced = mix(vec3(1.0), color, HELION_LIGHT_SATURATION);
    balanced *= dot(color, HELION_LIGHT_LUMA) / max(dot(balanced, HELION_LIGHT_LUMA), HELION_LIGHT_MIN_LUMA);
    float peak = max(balanced.r, max(balanced.g, balanced.b));
    return peak > HELION_LIGHT_MAX_CHANNEL ? balanced * (HELION_LIGHT_MAX_CHANNEL / peak) : balanced;
}

#endif
