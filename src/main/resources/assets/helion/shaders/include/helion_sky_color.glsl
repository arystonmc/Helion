#ifndef HELION_SKY_COLOR_GLSL
#define HELION_SKY_COLOR_GLSL

#include <helion:helion_color.glsl>
#include <helion:helion_atmosphere.glsl>
#include <helion:helion_sky.glsl>
#include <helion:helion_night.glsl>

uniform sampler2D SunSkyViewSampler;
uniform sampler2D SunMieViewSampler;
uniform sampler2D MoonSkyViewSampler;
uniform sampler2D MoonMieViewSampler;

const float HELION_RAIN_DARKENING = 0.35;
const float HELION_RAY_NEAR_DEPTH = 1.0;
const float HELION_RAY_FAR_DEPTH = 0.01;
const float HELION_DAY_SATURATION = 1.12;
const float HELION_GOLDEN_SATURATION = 1.3;
const float HELION_SCOTOPIC_LOW = 0.002;
const float HELION_SCOTOPIC_HIGH = 0.015;
const float HELION_SCOTOPIC_SHIFT = 0.7;
const vec3 HELION_SCOTOPIC_TINT = vec3(0.56, 0.7, 1.0);
const float HELION_BELOW_HORIZON_FADE = 0.12;
const float HELION_BELOW_HORIZON_DIMMING = 0.45;

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

vec3 helionWeathered(vec3 radiance) {
    float luminance = helionLuminance(radiance);
    return mix(vec3(luminance * HELION_RAIN_DARKENING), radiance, RainBrightness);
}

vec3 helionSkyRadiance(vec3 direction) {
    vec3 visible = helionAboveHorizon(direction);
    vec3 radiance = helionSkyViewRadiance(SunSkyViewSampler, SunMieViewSampler, visible, SunDirection) * SunIlluminance
        + helionSkyViewRadiance(MoonSkyViewSampler, MoonMieViewSampler, visible, MoonDirection) * MoonIlluminance
        + helionTwilightGlow(visible, SunDirection, SunIlluminance)
        + helionNightGlow(visible, SunDirection.y);
    float below = smoothstep(0.0, HELION_BELOW_HORIZON_FADE, -direction.y);
    return helionWeathered(radiance * mix(1.0, HELION_BELOW_HORIZON_DIMMING, below));
}

vec3 helionGradedSky(vec3 radiance) {
    float luminance = helionLuminance(radiance);
    float saturation = mix(HELION_DAY_SATURATION, HELION_GOLDEN_SATURATION, helionGoldenHour(SunDirection.y));
    vec3 graded = max(mix(vec3(luminance), radiance, saturation), 0.0);
    float scotopic = HELION_SCOTOPIC_SHIFT * (1.0 - smoothstep(HELION_SCOTOPIC_LOW, HELION_SCOTOPIC_HIGH, luminance));
    vec3 night = HELION_SCOTOPIC_TINT * (luminance / helionLuminance(HELION_SCOTOPIC_TINT));
    return mix(graded, night, scotopic);
}

vec3 helionEncodedSky(vec3 radiance) {
    return helionLinearToSrgb(helionNeutral(helionGradedSky(radiance)));
}

vec3 helionSkyColor(vec3 direction) {
    return helionEncodedSky(helionSkyRadiance(direction));
}

vec3 helionSkyColorWithCelestials(vec3 direction) {
    float pixel = max(max(length(dFdx(direction)), length(dFdy(direction))), HELION_MIN_DISC_EDGE);
    vec3 sky = helionSkyRadiance(direction);
    float moonCover = helionDiscCoverage(length(direction - MoonDirection), HELION_MOON_ANGULAR_RADIUS);
    vec3 night = helionNightSky(direction, pixel, helionLuminance(sky)) * (1.0 - moonCover);
    vec3 celestials = helionSunDisc(direction, SunDirection, SunIlluminance) + helionMoonDisc(direction, MoonDirection, MoonPhaseAngle);
    return helionEncodedSky(sky + night + celestials * RainBrightness);
}

vec3 helionViewPosition(vec2 screenUv, float deviceDepth) {
    vec4 world = InverseViewProjection * vec4(screenUv * 2.0 - 1.0, deviceDepth, 1.0);
    return world.xyz / world.w;
}

vec3 helionViewRay(vec2 screenUv) {
    return normalize(helionViewPosition(screenUv, HELION_RAY_FAR_DEPTH) - helionViewPosition(screenUv, HELION_RAY_NEAR_DEPTH));
}

#endif
