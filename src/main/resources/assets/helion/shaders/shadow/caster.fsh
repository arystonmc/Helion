#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in float vertexAlpha;

const float HELION_BASE_LEVEL = 0.0;

void main() {
    #ifdef ALPHA_CUTOUT
    if (textureLod(Sampler0, texCoord0, HELION_BASE_LEVEL).a * vertexAlpha < ALPHA_CUTOUT) {
        discard;
    }
    #endif
}
