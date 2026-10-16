#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif
#include <helion:helion_terrain_vertex.glsl>

#ifdef MULTIDRAW_TERRAIN
layout(location = 4) in ivec3 ChunkPosition;
layout(location = 5) in float ChunkVisibility;
#endif

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out float vertexAlpha;
layout(location = 2) out vec3 cameraRelativePosition;

void main() {
    vec3 pos = helion_vertex_position() + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    texCoord0 = helion_vertex_uv();
    vertexAlpha = Color.a;
    cameraRelativePosition = pos;
}
