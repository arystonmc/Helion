#ifndef HELION_LIGHTING_GLSL
#define HELION_LIGHTING_GLSL

layout(std140) uniform HelionLighting {
    float SkyFactor;
    float BlockFactor;
    float NightVisionFactor;
    float DarknessScale;
    float BossOverlayDarkening;
    float Brightness;
    float BlockLightIntensity;
    float SkyLightIntensity;
    vec3 BlockLightTint;
    vec3 SkyLightColor;
    vec3 AmbientColor;
    vec3 NightVisionColor;
};

const float HELION_LIGHT_LEVELS = 15.0;
const float HELION_BRIGHTNESS_CURVE = 3.0;
const float HELION_BLOCK_TINT_FADE = 0.9;
const vec3 HELION_BOSS_DARKENING_TINT = vec3(0.7, 0.6, 0.6);

float helionLevelBrightness(float level) {
    return level / (HELION_BRIGHTNESS_CURVE + 1.0 - HELION_BRIGHTNESS_CURVE * level);
}

vec3 helionSkyTerm(float level) {
    return SkyLightColor * helionLevelBrightness(level) * SkyFactor;
}

vec3 helionBlockTerm(float level) {
    float centered = 2.0 * level - 1.0;
    vec3 color = mix(BlockLightTint, vec3(1.0), HELION_BLOCK_TINT_FADE * centered * centered);
    return color * helionLevelBrightness(level) * BlockFactor;
}

vec3 helionInterpolatedSkyTerm(float coordinate) {
    float level = clamp(coordinate, 0.0, 1.0) * HELION_LIGHT_LEVELS;
    float lower = floor(level);
    float upper = min(lower + 1.0, HELION_LIGHT_LEVELS);
    return mix(helionSkyTerm(lower / HELION_LIGHT_LEVELS), helionSkyTerm(upper / HELION_LIGHT_LEVELS), level - lower);
}

vec3 helionInterpolatedBlockTerm(float coordinate) {
    float level = clamp(coordinate, 0.0, 1.0) * HELION_LIGHT_LEVELS;
    float lower = floor(level);
    float upper = min(lower + 1.0, HELION_LIGHT_LEVELS);
    return mix(helionBlockTerm(lower / HELION_LIGHT_LEVELS), helionBlockTerm(upper / HELION_LIGHT_LEVELS), level - lower);
}

vec3 helionVanillaBrightness(vec3 light) {
    float peak = max(light.r, max(light.g, light.b));
    if (peak <= 0.0) {
        return light;
    }
    float inverted = 1.0 - peak;
    float lifted = 1.0 - inverted * inverted * inverted * inverted;
    return mix(light, light * (lifted / peak), Brightness);
}

#endif
