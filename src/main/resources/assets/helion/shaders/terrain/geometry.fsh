#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif
#include <helion:helion_geometry.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) in vec3 cameraRelativePosition;
layout(location = 6) in vec4 surfaceColor;
layout(location = 7) in vec2 lightLevels;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 geometryNormal;
layout(location = 2) out vec4 geometryLight;
layout(location = 3) out vec4 geometryAlbedo;

vec3 faceNormal(vec3 position) {
    vec3 normal = normalize(cross(dFdx(position), dFdy(position)));
    return dot(normal, position) > 0.0 ? -normal : normal;
}

void main() {
    vec4 texel = UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize);
    vec4 color = texel * vertexColor;
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    geometryNormal = vec4(helionEncodeNormal(faceNormal(cameraRelativePosition)), HELION_GEOMETRY_PRESENT);
    float fogAmount = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    geometryLight = vec4(lightLevels, chunkVisibility, HELION_GEOMETRY_PRESENT);
    geometryAlbedo = vec4((texel * surfaceColor).rgb, fogAmount);
}
