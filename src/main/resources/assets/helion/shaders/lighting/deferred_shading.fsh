#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_lighting.glsl>

uniform sampler2D LightBufferSampler;
uniform sampler2D AlbedoSampler;
uniform sampler2D GeometryLightSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const vec3 HELION_WHITE_ALBEDO = vec3(1.0);
const float HELION_DISPLAY_LIGHT_LIMIT = 1.0;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 light = texelFetch(LightBufferSampler, pixel, 0);
    if (light.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        discard;
    }
    vec4 albedo = texelFetch(AlbedoSampler, pixel, 0);
    float chunkVisibility = texelFetch(GeometryLightSampler, pixel, 0).b;
    #ifdef HELION_LIGHT_ONLY
    vec3 surface = HELION_WHITE_ALBEDO;
    #else
    vec3 surface = albedo.rgb;
    #endif
    vec3 vanillaColor = surface * helionVanillaBrightness(helionLinearToSrgb(light.rgb));
    vec3 radiance = helionInverseNeutral(helionSrgbToLinear(vanillaColor)) * max(light.rgb / light.a, vec3(HELION_DISPLAY_LIGHT_LIMIT));
    vec3 color = helionLinearToSrgb(helionNeutral(radiance));
    color = mix(FogColor.rgb, color, chunkVisibility);
    color = mix(color, FogColor.rgb, albedo.a * FogColor.a);
    fragColor = vec4(color, 1.0);
}
