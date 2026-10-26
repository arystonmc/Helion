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

#ifdef HELION_PATH_TRACED
uniform sampler2D PathTracedSampler;

const float HELION_PATH_TRACED_SKY_GATE = 0.2;
const float HELION_PATH_TRACED_GAMMA = 1.0 / 2.2;

vec3 helionPathTracedSky(ivec2 pixel, vec4 levels) {
    vec3 sun;
    vec3 moon;
    vec3 dome;
    helionSkyLightParts(pixel, sun, moon, dome);
    vec3 ground = texelFetch(SkyLightSampler, HELION_SUN_TEXEL, 0).rgb * max(SunDirection.y, 0.0)
        + texelFetch(SkyLightSampler, HELION_MOON_TEXEL, 0).rgb * max(MoonDirection.y, 0.0)
        + texelFetch(SkyLightSampler, HELION_HEMISPHERE_TEXEL, 0).rgb;
    float full = pow(helionLuminance(helionInterpolatedSkyTerm(1.0, SkyLightColor) * SkyLightIntensity), 1.0 / HELION_PATH_TRACED_GAMMA);
    float scale = full / max(helionLuminance(ground), HELION_MIN_SKY_LUMINANCE);
    float shadow = 1.0 - texelFetch(ShadowMaskSampler, pixel, 0).r;
    vec3 direct = (sun * (1.0 - SunShadowStrength * shadow) + moon * (1.0 - MoonShadowStrength * shadow)) * scale
        * smoothstep(0.0, HELION_PATH_TRACED_SKY_GATE, levels.g);
    vec2 screen = (vec2(pixel) + 0.5) / vec2(textureSize(GeometryNormalSampler, 0));
    vec3 linear = direct + texture(PathTracedSampler, screen).rgb;
    return pow(max(linear, vec3(0.0)), vec3(HELION_PATH_TRACED_GAMMA));
}
#endif

vec3 helionDeferredBase(ivec2 pixel, vec4 levels) {
    #ifdef HELION_PATH_TRACED
    return helionAmbientTerm() + helionPathTracedSky(pixel, levels);
    #else
    vec3 sky = helionInterpolatedSkyTerm(levels.g, helionSkyLightColor(pixel));
    return helionAmbientTerm() + sky * SkyLightIntensity;
    #endif
}

vec4 helionDeferredLightWithBlock(ivec2 pixel, vec4 levels, vec3 block) {
    vec3 base = helionDeferredBase(pixel, levels);
    vec3 light = helionAdjustLight(base + helionShapedBlockLight(block, base) * BlockLightIntensity);
    vec3 referenceSky = helionInterpolatedSkyTerm(levels.g, SkyLightColor);
    float reference = max(helionMaxComponent(helionAdjustLight(helionAmbientTerm() + referenceSky)) * HELION_SKY_ADAPTATION, HELION_MIN_REFERENCE_LIGHT);
    return vec4(light, reference);
}

vec4 helionDeferredLight(ivec2 pixel, vec4 levels) {
    return helionDeferredLightWithBlock(pixel, levels, helionInterpolatedBlockTerm(levels.r));
}

#endif
