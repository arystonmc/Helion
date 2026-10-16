#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D SmoothedSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(texelFetch(SmoothedSampler, ivec2(gl_FragCoord.xy), 0).rgb, 1.0);
}
