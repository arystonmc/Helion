#ifndef HELION_TERRAIN_VERTEX_GLSL
#define HELION_TERRAIN_VERTEX_GLSL

#ifdef KINETIC_COMPACT_VERTICES
layout(location = 0) in uvec4 PositionLight;
layout(location = 1) in vec4 Color;
layout(location = 2) in uvec2 PackedUV0;

const float HELION_COMPACT_POSITION_STEP = 1.0 / 2048.0;
const float HELION_COMPACT_POSITION_ORIGIN = 8.0;
const float HELION_COMPACT_UV_STEP = 1.0 / 65536.0;
const uint HELION_COMPACT_LIGHT_MASK = 0xFFu;
const uint HELION_COMPACT_SKY_LIGHT_SHIFT = 8u;

vec3 helion_vertex_position() {
    return vec3(PositionLight.xyz) * HELION_COMPACT_POSITION_STEP - HELION_COMPACT_POSITION_ORIGIN;
}

vec2 helion_vertex_uv() {
    return vec2(PackedUV0) * HELION_COMPACT_UV_STEP;
}

ivec2 helion_vertex_light() {
    return ivec2(PositionLight.w & HELION_COMPACT_LIGHT_MASK, PositionLight.w >> HELION_COMPACT_SKY_LIGHT_SHIFT);
}
#else
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;

vec3 helion_vertex_position() {
    return Position;
}

vec2 helion_vertex_uv() {
    return UV0;
}

ivec2 helion_vertex_light() {
    return UV2;
}
#endif

#endif
