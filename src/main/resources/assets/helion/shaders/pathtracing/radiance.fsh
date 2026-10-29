#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <helion:helion_path_tracing.glsl>

uniform sampler2D IrradianceSampler;
uniform sampler2D SpecularSampler;
uniform sampler2D AlbedoSampler;
uniform sampler2D GeometryLightSampler;
uniform sampler2D GeometryNormalSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 levels = texelFetch(GeometryLightSampler, pixel, 0);
    if (levels.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        discard;
    }
    vec4 albedo = texelFetch(AlbedoSampler, pixel, 0);
    vec3 normal = normalize(texelFetch(GeometryNormalSampler, pixel, 0).rgb * 2.0 - 1.0);
    vec3 surface = helionIsEmissiveGeometry(levels.a) ? helionSrgbToLinear(albedo.rgb) : helionSurfaceAlbedo(albedo.rgb, normal);
    float diffuse = helionIsMetalGeometry(levels.a) ? 0.0 : 1.0;
    vec3 color = helionLinearToSrgb(helionNeutral(surface * texelFetch(IrradianceSampler, pixel, 0).rgb * diffuse + texelFetch(SpecularSampler, pixel, 0).rgb));
    color = mix(FogColor.rgb, color, levels.b);
    color = mix(color, FogColor.rgb, albedo.a * FogColor.a);
    fragColor = vec4(helionDitherEncoded(color, gl_FragCoord.xy), 1.0);
}
