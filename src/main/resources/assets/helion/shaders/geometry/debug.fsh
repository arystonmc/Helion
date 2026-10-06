#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_geometry.glsl>

#ifndef HELION_GEOMETRY_VIEW
#define HELION_GEOMETRY_VIEW 0
#endif

uniform sampler2D NormalSampler;
uniform sampler2D LightSampler;
uniform sampler2D AlbedoSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int HELION_VIEW_NORMALS = 1;
const int HELION_VIEW_BLOCK_LIGHT = 2;
const int HELION_VIEW_SKY_LIGHT = 3;
const vec3 HELION_EMPTY_COLOR = vec3(0.1);

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 light = texelFetch(LightSampler, pixel, 0);
    if (light.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        fragColor = vec4(HELION_EMPTY_COLOR, 1.0);
        return;
    }
    vec3 color = texelFetch(AlbedoSampler, pixel, 0).rgb;
    if (HELION_GEOMETRY_VIEW == HELION_VIEW_NORMALS) {
        color = texelFetch(NormalSampler, pixel, 0).rgb;
    } else if (HELION_GEOMETRY_VIEW == HELION_VIEW_BLOCK_LIGHT) {
        color = vec3(light.r);
    } else if (HELION_GEOMETRY_VIEW == HELION_VIEW_SKY_LIGHT) {
        color = vec3(light.g);
    }
    fragColor = vec4(color, 1.0);
}
