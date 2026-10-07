#ifndef HELION_SKY_LIGHT_GLSL
#define HELION_SKY_LIGHT_GLSL

const float HELION_MIN_SKY_LUMINANCE = 1.0e-6;

#ifdef HELION_PHYSICAL_SKY_LIGHT
uniform sampler2D GeometryNormalSampler;
uniform sampler2D SkyLightSampler;

const ivec2 HELION_SUN_TEXEL = ivec2(0, 0);
const ivec2 HELION_MOON_TEXEL = ivec2(1, 0);
const ivec2 HELION_HEMISPHERE_TEXEL = ivec2(2, 0);

void helionSkyLightParts(ivec2 pixel, out vec3 sun, out vec3 moon, out vec3 dome) {
    vec3 normal = texelFetch(GeometryNormalSampler, pixel, 0).rgb * 2.0 - 1.0;
    sun = texelFetch(SkyLightSampler, HELION_SUN_TEXEL, 0).rgb * max(dot(normal, SunDirection), 0.0);
    moon = texelFetch(SkyLightSampler, HELION_MOON_TEXEL, 0).rgb * max(dot(normal, MoonDirection), 0.0);
    dome = texelFetch(SkyLightSampler, HELION_HEMISPHERE_TEXEL, 0).rgb;
}
#endif

#ifdef HELION_SHADOWS
uniform sampler2D ShadowMaskSampler;

vec3 helionShadowTransmission(ivec2 pixel, float skyShare) {
    vec3 sun;
    vec3 moon;
    vec3 dome;
    helionSkyLightParts(pixel, sun, moon, dome);
    vec3 lit = sun + moon + dome;
    if (helionLuminance(lit) <= HELION_MIN_SKY_LUMINANCE) {
        return vec3(1.0);
    }
    float shadow = 1.0 - texelFetch(ShadowMaskSampler, pixel, 0).r;
    vec3 shaded = sun * (1.0 - SunShadowStrength * shadow) + moon * (1.0 - MoonShadowStrength * shadow) + dome;
    vec3 transmission = shaded / max(lit, vec3(HELION_MIN_SKY_LUMINANCE));
    return mix(vec3(1.0), transmission, clamp(skyShare, 0.0, 1.0));
}
#endif

#endif
