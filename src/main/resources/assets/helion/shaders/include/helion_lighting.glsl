#ifndef HELION_LIGHTING_GLSL
#define HELION_LIGHTING_GLSL

#include <helion:helion_light_level.glsl>

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
    vec3 SunDirection;
    vec3 MoonDirection;
    float SunShadowStrength;
    float MoonShadowStrength;
};

const float HELION_BLOCK_TINT_FADE = 0.9;
const vec3 HELION_BOSS_DARKENING_TINT = vec3(0.7, 0.6, 0.6);
const float HELION_BLOCK_LIGHT_GAIN = 1.15;
const float HELION_BLOCK_LIGHT_FALLOFF = 1.25;
const float HELION_DAYLIGHT_FROM = 0.15;
const float HELION_DAYLIGHT_TO = 0.6;
const float HELION_AMBIENT_SHARE = 0.6;
const float HELION_NIGHT_SKY_SHARE = 0.5;
const float HELION_NIGHT_SKY_FROM = 0.3;
const float HELION_NIGHT_SKY_TO = 0.9;
const float HELION_BRIGHTNESS_LIFT_SHARE = 0.7;

float helionNightSkyShare() {
    return mix(HELION_NIGHT_SKY_SHARE, 1.0, smoothstep(HELION_NIGHT_SKY_FROM, HELION_NIGHT_SKY_TO, SkyFactor));
}

vec3 helionSkyTerm(float level, vec3 skyColor) {
    return skyColor * helionLevelBrightness(level) * SkyFactor * helionNightSkyShare();
}

vec3 helionShapedBlockLight(vec3 block, vec3 base) {
    float peak = max(block.r, max(block.g, block.b));
    if (peak <= 0.0) {
        return block;
    }
    float shaped = HELION_BLOCK_LIGHT_GAIN * pow(peak, HELION_BLOCK_LIGHT_FALLOFF - 1.0);
    float daylight = smoothstep(HELION_DAYLIGHT_FROM, HELION_DAYLIGHT_TO, max(base.r, max(base.g, base.b)));
    return block * mix(shaped, 1.0, daylight);
}

vec3 helionBlockTerm(float level) {
    float centered = 2.0 * level - 1.0;
    vec3 color = mix(BlockLightTint, vec3(1.0), HELION_BLOCK_TINT_FADE * centered * centered);
    return color * helionLevelBrightness(level) * BlockFactor;
}

vec3 helionInterpolatedSkyTerm(float coordinate, vec3 skyColor) {
    float level = clamp(coordinate, 0.0, 1.0) * HELION_LIGHT_LEVELS;
    float lower = floor(level);
    float upper = min(lower + 1.0, HELION_LIGHT_LEVELS);
    return mix(helionSkyTerm(lower / HELION_LIGHT_LEVELS, skyColor), helionSkyTerm(upper / HELION_LIGHT_LEVELS, skyColor), level - lower);
}

vec3 helionInterpolatedBlockTerm(float coordinate) {
    float level = clamp(coordinate, 0.0, 1.0) * HELION_LIGHT_LEVELS;
    float lower = floor(level);
    float upper = min(lower + 1.0, HELION_LIGHT_LEVELS);
    return mix(helionBlockTerm(lower / HELION_LIGHT_LEVELS), helionBlockTerm(upper / HELION_LIGHT_LEVELS), level - lower);
}

vec3 helionAmbientTerm() {
    return max(AmbientColor * HELION_AMBIENT_SHARE, NightVisionColor * NightVisionFactor);
}

vec3 helionAdjustLight(vec3 light) {
    light = mix(light, light * HELION_BOSS_DARKENING_TINT, BossOverlayDarkening);
    return helionSrgbToLinear(max(light - vec3(DarknessScale), 0.0));
}

vec3 helionVanillaBrightness(vec3 light) {
    float peak = max(light.r, max(light.g, light.b));
    if (peak <= 0.0) {
        return light;
    }
    float inverted = 1.0 - peak;
    float lifted = 1.0 - inverted * inverted * inverted * inverted;
    return mix(light, light * (lifted / peak), Brightness * HELION_BRIGHTNESS_LIFT_SHARE);
}

#endif
