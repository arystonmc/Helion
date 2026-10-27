#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>

uniform sampler2D RadianceSampler;
uniform sampler2D AlbedoSampler;
uniform sampler2D GeometryLightSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 levels = texelFetch(GeometryLightSampler, pixel, 0);
    if (levels.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        discard;
    }
    vec3 color = helionLinearToSrgb(helionNeutral(texelFetch(RadianceSampler, pixel, 0).rgb));
    color = mix(FogColor.rgb, color, levels.b);
    color = mix(color, FogColor.rgb, texelFetch(AlbedoSampler, pixel, 0).a * FogColor.a);
    fragColor = vec4(color, 1.0);
}
