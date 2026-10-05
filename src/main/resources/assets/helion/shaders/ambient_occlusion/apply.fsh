#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D OcclusionSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    float visibility = texelFetch(OcclusionSampler, ivec2(gl_FragCoord.xy), 0).r;
    fragColor = vec4(vec3(visibility), 1.0);
}
