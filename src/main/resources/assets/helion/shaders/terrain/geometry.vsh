#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif
#include <helion:helion_geometry.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
#ifdef MULTIDRAW_TERRAIN
layout(location = 4) in ivec3 ChunkPosition;
layout(location = 5) in float ChunkVisibility;
#endif

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out float chunkVisibility;
layout(location = 5) out vec3 cameraRelativePosition;
layout(location = 6) out vec4 surfaceColor;
layout(location = 7) out vec2 lightLevels;

const float HELION_CHUNK_FULLY_VISIBLE_RANGE = 16.0;

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;

    float dist = length(pos);
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - HELION_CHUNK_FULLY_VISIBLE_RANGE) / HELION_CHUNK_FULLY_VISIBLE_RANGE, 0.0, 1.0));

    cameraRelativePosition = pos;
    surfaceColor = Color;
    lightLevels = clamp(vec2(UV2) / HELION_MAX_LIGHT_COORDINATE, 0.0, 1.0);
}
