#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_lighting.glsl>

uniform sampler2D GeometryLightSampler;
#ifdef HELION_PHYSICAL_SKY_LIGHT
uniform sampler2D GeometryNormalSampler;
uniform sampler2D SkyLightSampler;
#endif

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_NO_LIGHT = 0.0;
const float HELION_MIN_REFERENCE_LIGHT = 1.0;
const float HELION_SKY_ADAPTATION = 8.0;
const float HELION_MIN_SKY_LUMINANCE = 1.0e-6;
const ivec2 HELION_SUN_TEXEL = ivec2(0, 0);
const ivec2 HELION_MOON_TEXEL = ivec2(1, 0);
const ivec2 HELION_HEMISPHERE_TEXEL = ivec2(2, 0);

vec3 helionAdjustLight(vec3 light) {
    light = mix(light, light * HELION_BOSS_DARKENING_TINT, BossOverlayDarkening);
    return helionSrgbToLinear(max(light - vec3(DarknessScale), 0.0));
}

vec3 helionSkyLightColor(ivec2 pixel) {
    #ifdef HELION_PHYSICAL_SKY_LIGHT
    vec3 normal = texelFetch(GeometryNormalSampler, pixel, 0).rgb * 2.0 - 1.0;
    vec3 irradiance = texelFetch(SkyLightSampler, HELION_SUN_TEXEL, 0).rgb * max(dot(normal, SunDirection), 0.0)
        + texelFetch(SkyLightSampler, HELION_MOON_TEXEL, 0).rgb * max(dot(normal, MoonDirection), 0.0)
        + texelFetch(SkyLightSampler, HELION_HEMISPHERE_TEXEL, 0).rgb;
    float luminance = helionLuminance(irradiance);
    if (luminance > HELION_MIN_SKY_LUMINANCE) {
        return irradiance / luminance * helionLuminance(SkyLightColor);
    }
    #endif
    return SkyLightColor;
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 levels = texelFetch(GeometryLightSampler, pixel, 0);
    if (levels.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        fragColor = vec4(HELION_NO_LIGHT);
        return;
    }
    vec3 ambient = max(AmbientColor, NightVisionColor * NightVisionFactor);
    vec3 sky = helionInterpolatedSkyTerm(levels.g, helionSkyLightColor(pixel));
    vec3 block = helionInterpolatedBlockTerm(levels.r);
    vec3 light = helionAdjustLight(ambient + sky * SkyLightIntensity + block * BlockLightIntensity);
    vec3 referenceSky = helionInterpolatedSkyTerm(levels.g, SkyLightColor);
    float reference = max(helionMaxComponent(helionAdjustLight(ambient + referenceSky)) * HELION_SKY_ADAPTATION, HELION_MIN_REFERENCE_LIGHT);
    fragColor = vec4(light, reference);
}
