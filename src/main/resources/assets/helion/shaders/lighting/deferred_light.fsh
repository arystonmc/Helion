#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_lighting.glsl>
#include <helion:helion_sky_light.glsl>

uniform sampler2D GeometryLightSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_NO_LIGHT = 0.0;
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

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 levels = texelFetch(GeometryLightSampler, pixel, 0);
    if (levels.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        fragColor = vec4(HELION_NO_LIGHT);
        return;
    }
    vec3 ambient = helionAmbientTerm();
    vec3 sky = helionInterpolatedSkyTerm(levels.g, helionSkyLightColor(pixel));
    vec3 block = helionInterpolatedBlockTerm(levels.r);
    vec3 light = helionAdjustLight(ambient + sky * SkyLightIntensity + block * BlockLightIntensity);
    vec3 referenceSky = helionInterpolatedSkyTerm(levels.g, SkyLightColor);
    float reference = max(helionMaxComponent(helionAdjustLight(ambient + referenceSky)) * HELION_SKY_ADAPTATION, HELION_MIN_REFERENCE_LIGHT);
    fragColor = vec4(light, reference);
}
