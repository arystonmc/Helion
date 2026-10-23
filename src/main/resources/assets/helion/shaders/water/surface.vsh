#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif
#include <helion:helion_geometry.glsl>
#include <helion:helion_terrain_vertex.glsl>

#ifdef MULTIDRAW_TERRAIN
layout(location = 4) in ivec3 ChunkPosition;
layout(location = 5) in float ChunkVisibility;
#endif

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec3 cameraRelativePosition;
layout(location = 2) out vec2 wavePosition;
layout(location = 3) out float vertexAlpha;
layout(location = 4) out float skyLight;

const int HELION_WAVE_WRAP = 1023;

void main() {
    vec3 local = helion_vertex_position();
    vec3 pos = local + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    texCoord0 = helion_vertex_uv();
    cameraRelativePosition = pos;
    wavePosition = vec2(ChunkPosition.xz & HELION_WAVE_WRAP) + local.xz;
    vertexAlpha = Color.a;
    skyLight = clamp(float(helion_vertex_light().y) / HELION_MAX_LIGHT_COORDINATE, 0.0, 1.0);
}
