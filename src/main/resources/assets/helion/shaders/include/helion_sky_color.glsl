#ifndef HELION_SKY_COLOR_GLSL
#define HELION_SKY_COLOR_GLSL

#include <helion:helion_color.glsl>
#include <helion:helion_atmosphere.glsl>
#include <helion:helion_sky.glsl>

uniform sampler2D SunSkyViewSampler;
uniform sampler2D SunMieViewSampler;
uniform sampler2D MoonSkyViewSampler;
uniform sampler2D MoonMieViewSampler;

const float HELION_RAIN_DARKENING = 0.35;
const float HELION_MIN_HORIZONTAL = 1.0e-5;
const float HELION_RAY_NEAR_DEPTH = 1.0;
const float HELION_RAY_FAR_DEPTH = 0.01;

vec3 helionSkyViewRadiance(sampler2D skyView, sampler2D mieView, vec3 direction, vec3 light) {
    float viewZenithAngle = acos(clamp(direction.y, -1.0, 1.0));
    float azimuth = 0.0;
    if (length(direction.xz) > HELION_MIN_HORIZONTAL && length(light.xz) > HELION_MIN_HORIZONTAL) {
        azimuth = acos(clamp(dot(normalize(direction.xz), normalize(light.xz)), -1.0, 1.0));
    }
    vec2 uv = helionSkyViewUv(ViewHeight, viewZenithAngle, azimuth);
    vec2 lookup = helionToSubUv(uv, HELION_SKY_VIEW_SIZE);
    return texture(skyView, lookup).rgb + texture(mieView, lookup).rgb * helionMiePhase(dot(direction, light));
}

vec3 helionAboveHorizon(vec3 direction) {
    return direction.y >= 0.0 ? direction : normalize(vec3(direction.x, 0.0, direction.z) + vec3(HELION_MIN_HORIZONTAL, 0.0, 0.0));
}

vec3 helionSkyRadiance(vec3 direction) {
    vec3 visible = helionAboveHorizon(direction);
    vec3 radiance = helionSkyViewRadiance(SunSkyViewSampler, SunMieViewSampler, visible, SunDirection) * SunIlluminance
        + helionSkyViewRadiance(MoonSkyViewSampler, MoonMieViewSampler, visible, MoonDirection) * MoonIlluminance;
    float luminance = helionLuminance(radiance);
    return mix(vec3(luminance * HELION_RAIN_DARKENING), radiance, RainBrightness);
}

vec3 helionSkyColor(vec3 direction) {
    return helionLinearToSrgb(helionNeutral(helionSkyRadiance(direction)));
}

vec3 helionViewPosition(vec2 screenUv, float deviceDepth) {
    vec4 world = InverseViewProjection * vec4(screenUv * 2.0 - 1.0, deviceDepth, 1.0);
    return world.xyz / world.w;
}

vec3 helionViewRay(vec2 screenUv) {
    return normalize(helionViewPosition(screenUv, HELION_RAY_FAR_DEPTH) - helionViewPosition(screenUv, HELION_RAY_NEAR_DEPTH));
}

#endif
