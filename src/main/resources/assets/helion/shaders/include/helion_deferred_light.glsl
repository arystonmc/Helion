#ifndef HELION_DEFERRED_LIGHT_GLSL
#define HELION_DEFERRED_LIGHT_GLSL

const float HELION_MIN_REFERENCE_LIGHT = 1.0;
const float HELION_SKY_ADAPTATION = 8.0;
const float HELION_NIGHT_SUN_LOW = -0.1;
const float HELION_NIGHT_SUN_HIGH = 0.05;

vec3 helionSkyLightColor(ivec2 pixel) {
    #ifdef HELION_PHYSICAL_SKY_LIGHT
    vec3 sun;
    vec3 moon;
    vec3 dome;
    helionSkyLightParts(pixel, sun, moon, dome);
    float luminance = helionLuminance(sun + moon + dome);
    if (luminance > HELION_MIN_SKY_LUMINANCE) {
        vec3 physical = (sun + moon + dome) / luminance * helionLuminance(SkyLightColor);
        float night = 1.0 - smoothstep(HELION_NIGHT_SUN_LOW, HELION_NIGHT_SUN_HIGH, SunDirection.y);
        return mix(physical, SkyLightColor, night);
    }
    #endif
    return SkyLightColor;
}

vec3 helionDeferredBase(ivec2 pixel, vec4 levels) {
    vec3 sky = helionInterpolatedSkyTerm(levels.g, helionSkyLightColor(pixel));
    return helionAmbientTerm() + sky * SkyLightIntensity;
}

vec4 helionDeferredLightWithBlock(ivec2 pixel, vec4 levels, vec3 block) {
    vec3 light = helionAdjustLight(helionDeferredBase(pixel, levels) + block * BlockLightIntensity);
    vec3 referenceSky = helionInterpolatedSkyTerm(levels.g, SkyLightColor);
    float reference = max(helionMaxComponent(helionAdjustLight(helionAmbientTerm() + referenceSky)) * HELION_SKY_ADAPTATION, HELION_MIN_REFERENCE_LIGHT);
    return vec4(light, reference);
}

vec4 helionDeferredLight(ivec2 pixel, vec4 levels) {
    return helionDeferredLightWithBlock(pixel, levels, helionInterpolatedBlockTerm(levels.r));
}

#endif
