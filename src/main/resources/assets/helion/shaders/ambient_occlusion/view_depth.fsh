#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_view.glsl>

uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    float deviceDepth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    float viewDepth = deviceDepth == HELION_SKY_DEVICE_DEPTH ? HELION_SKY_VIEW_DEPTH : helionLinearDepth(deviceDepth);
    fragColor = vec4(viewDepth, 0.0, 0.0, 1.0);
}
