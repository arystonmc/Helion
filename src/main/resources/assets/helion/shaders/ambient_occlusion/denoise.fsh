#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_ambient_occlusion_denoise.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    float occlusion = helionDenoise(ivec2(gl_FragCoord.xy), false);
    fragColor = vec4(occlusion, 0.0, 0.0, 1.0);
}
