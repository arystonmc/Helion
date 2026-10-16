#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_temporal.glsl>

uniform sampler2D HistorySampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 internalPixel = min(ivec2(gl_FragCoord.xy * InverseScreenSize * InputSize), ivec2(InputSize) - 1);
    fragColor = vec4(texelFetch(HistorySampler, ivec2(gl_FragCoord.xy), 0).rgb, 1.0);
    gl_FragDepth = texelFetch(DepthSampler, internalPixel, 0).r;
}
